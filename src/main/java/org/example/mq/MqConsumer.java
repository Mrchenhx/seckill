package org.example.mq;

import com.alibaba.fastjson.JSON;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.*;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.example.dao.ItemStockDOMapper;
import org.example.error.BusinessException;
import org.example.error.EmBusinessError;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * @Author: Richard
 * @Create: 2021/07/17 14:45:00
 * @Description: TODO
 */
@Component
public class MqConsumer {

    private DefaultMQPushConsumer consumer;

    @Value("${mq.nameserver.addr}")
    private String nameAddr;
    @Value("${mq.topicname}")
    private String topicName;
    @Resource
    private ItemStockDOMapper itemStockDOMapper;

    @PostConstruct
    public void init() throws MQClientException {
        consumer = new DefaultMQPushConsumer("stock_consumer_group");
        consumer.setNamesrvAddr(nameAddr);
        // 订阅所有消息
        consumer.subscribe(topicName,"*");
        // 监听消息队列，如果消费则返回success
        consumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
                // 消费消息
                Message msg = msgs.get(0);
                String jsonString = new String(msg.getBody());
                // 解析消息队列存放的body
                Map<String, Object> map = JSON.parseObject(jsonString, Map.class);
                Integer itemId = (Integer) map.get("itemId");
                Integer amount = (Integer) map.get("amount");
                // 同步数据库的库存
                itemStockDOMapper.decreaseStock(itemId, amount);
                try {
                    throw new BusinessException(EmBusinessError.MQ_SEND_FAIL);
                } catch (BusinessException e) {
                    System.out.println("消费失败，看是否回滚！！");
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });
        consumer.start();
    }

}
