package com.rc.server;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint("/imserver/{userId}")
@Component
public class WebSocketServer {

    /**静态变量，用来记录当前在线连接数。应该把它设计成线程安全的。*/
    private static int onlineCount = 0;
    /**concurrent包的线程安全Set，用来存放每个客户端对应的MyWebSocket对象。*/
    private static ConcurrentHashMap<String,WebSocketServer> webSocketMap = new ConcurrentHashMap<>();
    /**与某个客户端的连接会话，需要通过它来给客户端发送数据*/
    private Session session;
    /**接收userId*/
    private String userId="";

    /**
     * 发送自定义消息
     * @param message
     */
    public static void sendInfo(String message, @PathParam("userId") String userId) throws IOException {
        System.out.println("发送消息到:"+userId+"，报文:"+message);
        if(!StringUtils.isEmpty(webSocketMap)&&webSocketMap.containsKey(userId)){
                webSocketMap.get(userId).sendMessage(message);
        }else {
            System.out.println("用户"+userId+",不在线！");
        }
    }

    /**
     * 连接建立成功的方法
     */
    @OnOpen
    public void open(Session session, @PathParam("userId")String userId){
        this.session=session;
        this.userId=userId;
        if(webSocketMap.containsKey(userId)){
            webSocketMap.remove(userId);
            //add to set
            webSocketMap.put(userId,this);
        }else {
            //add to set
            webSocketMap.put(userId,this);
            //onlineCount++
            addOnlineCount();
        }
        System.out.println("用户连接:"+userId+",当前在线人数为:" + getOnlineCount());

        try {
            sendMessage("连接成功");
        } catch (IOException e) {
            System.out.println("用户:"+userId+",网络异常!!!!!!");
        }
    }

    /**
     * 实现服务器主动推送
     * @param msg
     */
    private void sendMessage(String msg) throws IOException {
        this.session.getBasicRemote().sendText(msg);
    }

    private int getOnlineCount() {
        return onlineCount;
    }

    private void addOnlineCount() {
        WebSocketServer.onlineCount++;
    }


    /**
     * 连接关闭的调用方法
     */

    @OnClose
    public void onClose(){
        if(webSocketMap.containsKey(userId)){
            //从set中移除
            webSocketMap.remove(userId);
            subOnlineCount();
        }
        System.out.println("用户退出:"+userId+",当前在线人数为:" + getOnlineCount());
    }

    private void subOnlineCount() {
        WebSocketServer.onlineCount--;
    }

    /**
     * 收到客户端消息的调用方法
     */
    @OnMessage
    public void onMessage(String msg,Session session){
        System.out.println("用户消息:"+userId+",报文:"+msg);
        //可以群发消息
        //消息保存到数据库、redis
        if(!StringUtils.isEmpty(msg)){

            try
            {
                //解析发送的报文
                JSONObject jsonObject = JSON.parseObject(msg);
                //追加发送人(防止串改)
                jsonObject.put("fromUserId",this.userId);
                String toUserId=jsonObject.getString("toUserId");
                //传送给对应toUserId用户的websocket
                if(!StringUtils.isEmpty(toUserId)&&webSocketMap.containsKey(toUserId)){
                    webSocketMap.get(toUserId).sendMessage(jsonObject.toJSONString());
                }else {
                    System.out.println("请求的userId:"+toUserId+"不在该服务器上");
                    //否则不在这个服务器上，发送到mysql或者redis
                }
            }catch (Exception e){
                e.printStackTrace();
            }


        }
    }
}
