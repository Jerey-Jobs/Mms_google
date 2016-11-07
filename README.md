# Mms_google
该项目为google官方Mms代码，我在源码基础上加了很多注释，帮助大家更好的了解Mms，以及google代码风格

---
title: Mms短信模块分析

---
大致分为：
 - UI分析
 - 短信发送、接收
 - 彩信
 - 数据库


### **基础知识**
Thread对话---是指用户与某个联系人或某几个联系人之间的一系列信息交互。在Mms中，用Thread Id来标识和管理对话，Thread Id也即在数据库表threads中的_id。可能用Conversation是更易于理解。但是Thread本身就有对话的意思，某些论坛中的一个帖子在英语里就叫Thread。Thread的词典释义是：”因特网上关于一个题目一连串的信息 (计算机用语)”，所以这里用Thread，也是比较恰当的。

Conversation--是用来管理Thread对话的，Conversation是一个Thread对话的抽象出来数据结构，它能够，从数据 库中查询，删除一个对话中的消息等，每一个Conversation有一个唯一的Thread Id。但是它也负责一些所有对话的管理，比如查询所有的对话，删除所有的对话等（这个应该是设计上面的问题）。
事实上，Conversation更多时候是充当前前对话的角色，比如在新新建信息时，编辑信息时，或是查看某个对话时，都会有一个 Conversation对象存在在，以代表当前信息所处的对话。它是一个近似单键，都是通过Conversation的静态方法来获得 Conversation对象，有一些其他的方法也是静态的。

ConversationList--负责显示和编辑所有的对话，以列表形式显示所有的Thread，每一项代表一个Thread，通常也会显示这个Thread的状态，如有无草稿，信息发送/接收是否成功等。

Message--消息，泛指短信SMS和彩信MMS。因为不再区分短信和彩信，在对话列表，草稿管理和信息列表中它们都是一样的，都是信息。 Message的数据结构是MessageItem，它是一个纯数据结构，里面存储着关于一个信息的所有数据，还有MessageListitem，它是 一个View，专门用于在消息列表中显示一个信息，里面的数据都是从MessageItem获取。它们统一都被 ComposeMessageActivity，MessageListAdapter和MessageListView来管理。

WorkingMessage--当前消息，它是专门用于代表当前正在创建和编辑的信息的数据结构无论是短信还是彩信，在创建和编辑的时候都放在一个WorkingMessage对象里面。这个对象也负责信息的发送，存储和存储为草稿。

Slideshow--在Mms应用里面，彩信是以Slideshow幻灯片的形式来展示的。一个彩信可以有多张幻灯片，每张幻灯片上面可以有图 片，文字，音频和视频，可以设置每张幻灯片的浏览时长，布局等，这里的幻灯片与Office中的PowerPoint有几分类似。幻灯片的数目限制是以彩 信允许的附件大小为上限，这个也与每张幻灯片上面的媒体大小有关。可以这样讲MMS就是以幻灯片形式存在的，创建的时候是一张幻灯片一张幻灯片的编辑，收 到的彩信或编辑完后，就可以一张张的放映浏览幻灯片。
需要注意的是以幻灯片方式显示彩信仅是应用程序层的处理方式，不同的信息应用程序会以不同的方式处理彩信，实际的彩信的数据是以标准的Pdu方式进行发送和接收，是应用程序在发送前把幻灯片转化成为Pdu，并在接收后把Pdu转化成为自己可识别的幻灯片。

Recipient接受人，这里是指信息的接收者，要么是一个陌生的电话号码，要么是一个陌生的电子邮件地址（彩信时），要么就是手机联系人数据库 中的联系人。彩信和短信对接收人的数量都有限制，这个也是在Mms的Settings时面可以更改的。每一条信息要想发送成功，必须保证接收人是一个合法 的联系人，合法也是不同的手机有不同的定义，但通常来讲，要么与联系人数据库中的某个联系人匹配，要么是一个电话号码，要么是一个电子邮件地址，其他情况 则视为不合法，对于有不合法接收人的信息，不会进行发送。管理联系人的数据结构是Contact和ContactList，其中ContactList是 一个以Contact为元素的ArrayList。Contact不但存储有联系人的一些信息，如名字，电话号码等，它还能与联系人数据库进行同步，也就 是它能保证它是一个合法的联络人，并在数据库中存在。在信息发送前会先进行一次联系人同步，以保证已有的联系人是正确的。


  ![Mms模块构成图][1]
###  UI主要组成
![会话列表数据刷新][2]
![编辑界面类图][3]
   
### 短信发送、接收 
![短信发送流程图][4]
![短信发送类图][5]
![短信接收流程图][6]

### 彩信 
MMS为Multimedia Messaging Service的缩写，中文译为多媒体短信服务，通过网络来传输数据

MMS发送和接收: 手机终端合成多媒体消息后，可以向网内的所有合法用户发送多媒体消息。不直接投递给接收方。先发送给一个中间服务器 (MMSC)。彩信网关暂时保存彩信，然后通过Push 协议给接收方发送一个Push 通知，接收方收到通知后再从MMSC 上获取彩信内容。

与MMSC交互的数据为PDU数据

Google内置包里为我们提供了一系列操作PDU的类（com.google.android.mms）

PduPersister  	用于管理PDU存储
PduParser	 	用于解析PDU
PduComposer	用于生成PDU

![彩信发送流程图][7]
![彩信接收流程图][8]


**彩信不自动下载的情况**

> 在会话界面的MessageListItem中会显示一个download按钮，当用户点击该按钮
> -->mContext.startService(intent);
> -->TransactionService中，处理Action为 RETRIEVE_TRANSACTION的请求
> --> transaction = new RetrieveTransaction();
> -->transaction.process();
> -->byte[] resp = getPdu(mContentLocation);
> -->PduParser解析数据成pdu
> -->isDuplicateMessage 判断是否是重复的短信
> -->PduPersister 再将其存储
> -->sendAcknowledgeInd(retrieveConf); 再发送Ack给MMSC
> -->notifyObservers();   通知service ，状态改变



 #### Mms数据库，在短信应用程序中占有很重要的地位

> 1.负责数据的存储 	短信，彩信，对话列表都存储在数据库中
> 
> 2.负责大量的通信 	先待发短信存储到数据库中，发送服务将待短信从数据库取出
> 
> 3.通过ContentProvider，间接的肩负起通知界面数据刷新工作  	getContext().getContentResolver().notifyChange() 	通知观察者去刷新数据


> 
> threads表：在ConversationList.Java中显示的当前短信 
> sms表：短信内容 
> pdu表： 彩信内容
> part表：（存储彩信内容（文本、音乐、图象）文件名 
> pending_msgs：存储待发送的短信与彩信 
> drm：用于彩信权限管理
> words：用于存储关键字，搜索时用
> SmsProvider用于短信相关数据的存取 MmsProvider用于彩信相关数据的存取
> MmsSmsProvider则用于短彩信通用数据的存取，如会话信息、接收者、草稿（公共属性）等


  [1]: https://github.com/Jerey-Jobs/Mms_google/blob/master/HelpPictures/%E5%9F%BA%E6%9C%AC%E7%BB%84%E6%88%90.png
  [2]: https://github.com/Jerey-Jobs/Mms_google/blob/master/HelpPictures/%E4%BC%9A%E8%AF%9D%E5%88%97%E8%A1%A8%E6%95%B0%E6%8D%AE%E5%88%B7%E6%96%B0.png
  [3]: https://github.com/Jerey-Jobs/Mms_google/blob/master/HelpPictures/%E7%BC%96%E8%BE%91%E7%95%8C%E9%9D%A2%E7%B1%BB%E5%9B%BE.png
  [4]: https://github.com/Jerey-Jobs/Mms_google/blob/master/HelpPictures/%E7%9F%AD%E4%BF%A1%E5%8F%91%E9%80%81%E6%B5%81%E7%A8%8B.png
  [5]: https://github.com/Jerey-Jobs/Mms_google/blob/master/HelpPictures/%E7%9F%AD%E4%BF%A1%E5%8F%91%E9%80%81%E6%8E%A5%E5%8F%97%E7%B1%BB%E5%9B%BE.png
  [6]: https://github.com/Jerey-Jobs/Mms_google/blob/master/HelpPictures/%E7%9F%AD%E4%BF%A1%E6%8E%A5%E6%94%B6%E6%B5%81%E7%A8%8B.png
  [7]: https://github.com/Jerey-Jobs/Mms_google/blob/master/HelpPictures/%E5%BD%A9%E4%BF%A1%E5%8F%91%E9%80%81%E6%B5%81%E7%A8%8Bgoogle.png
  [8]: https://github.com/Jerey-Jobs/Mms_google/blob/master/HelpPictures/%E5%BD%A9%E4%BF%A1%E6%8E%A5%E6%94%B6%E6%B5%81%E7%A8%8B.png