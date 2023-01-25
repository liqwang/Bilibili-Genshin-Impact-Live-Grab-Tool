联系我：QQ 836200779

## 运行效果

.<img src="README.pictures/image-20220831191210506.png" alt="image-20220831191210506" style="zoom:80%;" />



## 使用方法

1. 浏览器进入奖品页面

   .<img src="README.pictures/image-20220831185711482.png" alt="image-20220831185711482" style="zoom:80%;" />

2. F12打开开发者选项

3. 进入"网络"标签

4. 刷新，找到`single_task`开头的URL，保存CSRF

   .<img src="README.pictures/image-20230125190019190.png" alt="image-20230125190019190" style="zoom:80%;" />

4. 再在"请求标头"中找到Cookie，右键"复制值"

   .<img src="README.pictures/image-20220831184720930.png" alt="image-20220831184720930" style="zoom:80%;" />

5. 将获取的CSRF和Cookie填入

   .<img src="README.pictures/image-20220831184947526.png" alt="image-20220831184947526" style="zoom:80%;" />

6. 启动

7. 输入URL中的task_id

   .<img src="README.pictures/image-20220831185841325.png" alt="image-20220831185841325" style="zoom:80%;" />
