package chatroombyredis.controller;

/**
 * 功能：
 * 日期：2024/5/17 下午11:02
 */

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Controller
public class ChatController {

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    private final List<SseEmitter> clients = new CopyOnWriteArrayList<>();//    clients 用于存储所有的SseEmitter对象

    @GetMapping("/")
    public String index(Model model) {
        return "index";
    }

    /**
     * 发送消息
     * @param message
     */

    @PostMapping("/send")
    @ResponseBody// 防止解析为视图
    public void sendMessage(@RequestParam String message) {
        // 前端发送消息时，将消息发送到Redis的chat频道
        redisConnectionFactory.getConnection().publish("chat".getBytes(), message.getBytes());
    }

    /**
     * 通过SseEmitter实现服务器推送
     * @return
     */

    @GetMapping("/listen")
    public SseEmitter listen() {
        SseEmitter emitter = new SseEmitter();
        clients.add(emitter);// 将新的客户端添加到clients中
        emitter.onCompletion(() -> clients.remove(emitter));//  当客户端断开连接时，将其从clients中移除
        emitter.onTimeout(() -> clients.remove(emitter));   // 当客户端连接超时时，将其从clients中移除
        return emitter;
    }

    /**
     * 通过RedisMessageListenerContainer监听Redis消息
     * @param messageListener
     * @return
     */
    @Bean
    RedisMessageListenerContainer redisMessageListenerContainer(MessageListenerAdapter messageListener) {
        // 相当于邮局
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(messageListener, new ChannelTopic("chat"));// 创建了一个新的ChannelTopic对象，用于监听chat频道的消息
        return container;
    }

    /**
     * 通过MessageListenerAdapter适配器，将消息委托给MessageListener
     * @return
     */
    @Bean
    MessageListenerAdapter messageListener() {
        //  MessageListenerAdapter 广播接收器，接收电台的广播。
        //  MessageListener  广播主持人，接收到广播后，将消息传递给听众。
        return new MessageListenerAdapter((MessageListener) (message, pattern) -> {
            String msg = new String(message.getBody());
            for (SseEmitter emitter : clients) {
                try {
                    emitter.send(SseEmitter.event().data(msg));
                } catch (IOException e) {
                    clients.remove(emitter);
                }
            }//   为了将消息发送给所有的客户端
            // 为什么是emitter.send()而不是emmitter.recive()？
            // 因为服务器推送是服务器向客户端推送消息，
        });
    }
}
