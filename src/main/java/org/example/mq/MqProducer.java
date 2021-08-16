package org.example.mq;

import com.alibaba.fastjson.JSON;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.*;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.example.dao.StockLogDOMapper;
import org.example.dataobject.StockLogDO;
import org.example.error.BusinessException;
import org.example.error.EmBusinessError;
import org.example.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * @Author: Richard
 * @Create: 2021/07/17 14:45:00
 * @Description: TODO
 */
@Component
public class MqProducer {

    private DefaultMQProducer producer;

    // 事务型消息队列
    private TransactionMQProducer transactionMQProducer;

    @Value("${mq.nameserver.addr}")
    private String nameAddr;
    @Value("${mq.topicname}")
    private String topicName;

    @Autowired
    private OrderService orderService;
    @Resource
    private StockLogDOMapper stockLogDOMapper;

    @PostConstruct
    public void init() throws MQClientException {
        // mq producer 初始化
        producer = new DefaultMQProducer("producer_group");
        // 指定服务器地址
        producer.setNamesrvAddr(nameAddr);
        // 开启生产者实例
        producer.start();

        transactionMQProducer = new TransactionMQProducer("transaction_producer_group");
        transactionMQProducer.setNamesrvAddr(nameAddr);
        transactionMQProducer.start();

        // 监听器。监听消息发送后事务过程中的状态变化
        transactionMQProducer.setTransactionListener(new TransactionListener() {
            @Override
            public LocalTransactionState executeLocalTransaction(Message msg, Object arg) {
                // 真正要做的事 创建订单
                Integer itemId = (Integer) ((Map)arg).get("itemId");
                Integer userId = (Integer) ((Map)arg).get("userId");
                Integer amount = (Integer) ((Map)arg).get("amount");
                Integer promoId = (Integer) ((Map)arg).get("promoId");
                String stockLogId = (String)((Map)arg).get("stockLogId");
                try {
                    orderService.createOrder(userId, itemId, promoId, amount, stockLogId);
                } catch (BusinessException e) {
                    e.printStackTrace();
                    StockLogDO stockLogDO = stockLogDOMapper.selectByPrimaryKey(stockLogId);
                    stockLogDO.setStatus(3);
                    stockLogDOMapper.updateByPrimaryKey(stockLogDO);
                    return LocalTransactionState.ROLLBACK_MESSAGE;
                }
                return LocalTransactionState.COMMIT_MESSAGE;
            }

            @Override
            public LocalTransactionState checkLocalTransaction(MessageExt msg) {
                // 根据库存扣减是否成功，判断具体的状态
                String jsonString = new String(msg.getBody());
                // 解析消息队列存放的body
                Map<String, Object> map = JSON.parseObject(jsonString, Map.class);
                Integer itemId = (Integer) map.get("itemId");
                Integer amount = (Integer) map.get("amount");
                String stockLogId = (String) map.get("stockLogId");
                StockLogDO stockLogDO = stockLogDOMapper.selectByPrimaryKey(stockLogId);
                if(stockLogDO == null){
                    return LocalTransactionState.UNKNOW;
                }
                if(stockLogDO.getStatus().intValue() == 2) {
                    return LocalTransactionState.COMMIT_MESSAGE;
                }
                else if(stockLogDO.getStatus().intValue() == 1){
                    return LocalTransactionState.UNKNOW;
                }
                return LocalTransactionState.ROLLBACK_MESSAGE;
            }
        });
    }

    //事务型同步库存扣减消息
    public boolean transactionAsyncReduceStock(Integer userId,Integer itemId,
                                               Integer promoId,Integer amount,
                                                String stockLogId) {
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("itemId", itemId);
        bodyMap.put("amount", amount);
        bodyMap.put("stockLogId", stockLogId);

        Map<String, Object> argsMap = new HashMap<>();
        argsMap.put("itemId",itemId);
        argsMap.put("amount",amount);
        argsMap.put("userId",userId);
        argsMap.put("promoId",promoId);
        argsMap.put("stockLogId", stockLogId);

        Message message = new Message(topicName, "increase",
                JSON.toJSON(bodyMap).toString().getBytes(StandardCharsets.UTF_8));
        TransactionSendResult result = null;
        try {
            // 发送后状态为 prepare 状态，只有等 executeLocalTransaction 执行完毕后状态才会改变
            result = transactionMQProducer.sendMessageInTransaction(message, argsMap);
        } catch (MQClientException e) {
            e.printStackTrace();
            return false;
        }
        if(result.getLocalTransactionState() == LocalTransactionState.ROLLBACK_MESSAGE){
            return false;
        }else if(result.getLocalTransactionState() == LocalTransactionState.COMMIT_MESSAGE){
            return true;
        }else{
            return false;
        }
    }

        // 非事务型，
    // 同步库存扣减消息
    public boolean asyncReduceStock(Integer itemId, Integer amount) {
        Map<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("itemId", itemId);
        bodyMap.put("amount", amount);
        Message message = new Message(topicName, "increase",
                JSON.toJSON(bodyMap).toString().getBytes(StandardCharsets.UTF_8));
        try {
            // 消息直接发，不管是否能被接收
            producer.send(message);
        } catch (MQClientException e) {
            e.printStackTrace();
            return false;
        } catch (RemotingException e) {
            e.printStackTrace();
            return false;
        } catch (MQBrokerException e) {
            e.printStackTrace();
            return false;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
}
