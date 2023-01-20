import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("SpellCheckingInspection")
public class LiveGrabTool {
	//【每次启动前都要配置最新的cookie和csrf，否则可能有bug】
	private static final String COOKIE="xxxx";
	private static final String CSRF="xxxx";
	private static final int interval=1; //调速，隔xx毫秒发送一次请求

	private static final int printInterval=5000/(interval+9); //打印信息的间隔次数，防止打印信息刷屏
	volatile static boolean end = false; //抢奖品程序是否结束
	static String key = null;
	static String prizeName = null;
	static boolean satisfied=true; //**脚本运行前**领取条件是否满足

	@SuppressWarnings({"ConstantConditions","deprecation","unchecked"})
	public static void main(String[] args) throws IOException,InterruptedException{
		OkHttpClient client=new OkHttpClient.Builder()
											.readTimeout(1, TimeUnit.MINUTES)
											.build();
		ObjectMapper mapper = new ObjectMapper();
		System.out.print("请输入目标奖品的task_id:");
		String taskId=new Scanner(System.in).next(); //URL里的参数task_id，和最终post body里的`task_id`是两码事

		/*先验证领取条件的原因是，如果不满足领取条件，那么`infoUrl`的查询结果中的`receive_id`字段为0
		  这是直播系统的一个安全措施，只有满足领取条件系统才会告诉你真正的`receive_id`*/
		//1.等待领取条件满足
		System.out.println("等待领取条件满足...");
		String infoUrl=String.format("https://api.bilibili.com/x/activity/mission/single_task?csrf=%s&id=%s",CSRF,taskId);
		Request infoRequest =new Request.Builder()
										.url(infoUrl)
										.get()
										.addHeader("Cookie", COOKIE)
										.build();
		int receiveId;
		Map<String, Object> taskInfoMap;
		SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
		while(true){
			Response infoResponse = client.newCall(infoRequest).execute();
			//response body见info-response.json
			Map<String, Object> infoMap = mapper.readValue(infoResponse.body().string(), new TypeReference<>(){});
			infoResponse.close();
			taskInfoMap = (Map<String, Object>) ((Map<String, Object>) infoMap.get("data")).get("task_info");
			receiveId=(int)taskInfoMap.get("receive_id");
			if(receiveId==0){
				satisfied=false;
				System.out.println(dateFormat.format(new Date())+"领取条件仍不满足");
			}else{break;}
			Thread.sleep(1000); //一秒查询一次领取条件是否满足
		}
		Map<String,Object> groupListMap = ((ArrayList<Map<String,Object>>)taskInfoMap.get("group_list")).get(0);
		int actId=(int)groupListMap.get("act_id");
		int bodyTaskId=(int)groupListMap.get("task_id");
		int groupId=(int)groupListMap.get("group_id");

		//2.领取条件满足后，脚本触发，CPU使用率会接近100%
		System.out.printf("领取条件满足，脚本启动于%s\n",dateFormat.format(new Date()));
		FormBody clickBody =new FormBody.Builder()
										.add("csrf", CSRF.split("&")[0]) //去除csrf中的id字段
										.add("act_id", String.valueOf(actId))
										.add("task_id", String.valueOf(bodyTaskId))
										.add("group_id", String.valueOf(groupId))
										.add("receive_id", String.valueOf(receiveId))
										.add("receive_from","missionLandingPage")
										.build();
		Request clickRequest=new Request.Builder()
										.url("https://api.bilibili.com/x/activity/mission/task/reward/receive")
										.post(clickBody)
										.addHeader("Cookie",COOKIE)
										.build();
		AtomicInteger requestCount = new AtomicInteger();
		while(!end){
			new Thread(()->{
				try(Response response = client.newCall(clickRequest).execute()){
					String responseStr = response.body().string();
					Map<String,Object> jsonMap=mapper.readValue(responseStr,new TypeReference<>(){});
					Object message = jsonMap.get("message");
					if(message.equals("来晚了，奖品已被领完~")){
						//Response: {"code":75154,"message":"来晚了，奖品已被领完~","ttl":1,"data":null}
						Date curTime = new Date();
						if(satisfied){
							//当前为0:01之后判定为失败，这个判断条件用于[第一天没抢到，第二天0:00刷新剩余量]的情况
							if(curTime.getHours()==0 && curTime.getMinutes()>=1){
								end=true;
							}else if(requestCount.get()%printInterval==0){
								System.out.println(dateFormat.format(new Date())+"当日剩余量仍未刷新");
							}
						}else{end=true;}
					}else if((int)jsonMap.get("code")==0){
						//Response见success-response.json
						System.out.println("Success by "+Thread.currentThread().getName());
						Map<String, Object> dataMap = (Map<String, Object>) jsonMap.get("data");
						key=((Map<String, String>)dataMap.get("extra")).get("cdkey_content");
						prizeName=(String)dataMap.get("name");
						end=true;
					}else if(message.equals("请求过于频繁，请稍后再试")){
						//Response: {"code":-509,"message":"请求过于频繁，请稍后再试","ttl":1}
						if(requestCount.get()%printInterval==0)
							System.out.println("服务器繁忙");
					}else if(message.equals("任务奖励已领取")){
						//Response: {"code":75086,"message":"任务奖励已领取","ttl":1,"data":null}
						end=true;
					}else if(requestCount.get()>0){
						System.err.println("未考虑到的情况: "+responseStr);
					}
				}catch (IOException e){
					System.err.println("IOException at "+Thread.currentThread().getName());
				}
			},"Thread-"+ requestCount.incrementAndGet()).start();
			if(requestCount.get()%printInterval==0){
				System.out.printf("已发送%d次请求\n", requestCount.get());
			}
			Thread.sleep(interval);
		}
		System.out.printf("共发送了%d次请求，脚本结束于%s\n",requestCount.get(),dateFormat.format(new Date()));
		Thread.sleep(2*1000); //等待所有线程执行完毕
		if(key==null){
			System.out.println("奖品已被领完，抢奖品失败");
		}else{System.out.printf("抢奖品成功,获得【%s】,兑换码【%s】\n",prizeName,key);}
		System.exit(1);
	}
}
