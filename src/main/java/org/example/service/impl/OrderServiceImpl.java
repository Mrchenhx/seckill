package org.example.service.impl;

import org.example.dao.OrderDOMapper;
import org.example.dao.SequenceDOMapper;
import org.example.dao.StockLogDOMapper;
import org.example.dataobject.OrderDO;
import org.example.dataobject.SequenceDO;
import org.example.dataobject.StockLogDO;
import org.example.error.BusinessException;
import org.example.error.EmBusinessError;
import org.example.service.ItemService;
import org.example.service.OrderService;
import org.example.service.UserService;
import org.example.service.model.ItemModel;
import org.example.service.model.OrderModel;
import org.example.service.model.UserModel;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;


@Service
public class OrderServiceImpl implements OrderService {

    @Resource
    private SequenceDOMapper sequenceDOMapper;

    @Autowired
    private ItemService itemService;

    @Autowired
    private UserService userService;

    @Resource
    private OrderDOMapper orderDOMapper;

    @Resource
    private StockLogDOMapper stockLogDOMapper;


    @Override
    @Transactional
    public OrderModel createOrder(Integer userId, Integer itemId,
                                  Integer promoId, Integer amount,
                                  String stockLogId) throws BusinessException {
        //1.校验下单状态,下单的商品是否存在，用户是否合法，购买数量是否正确
        // ItemModel itemModel = itemService.getItemById(itemId);
        ItemModel itemModel = itemService.getItemByIdInCache(itemId);
        if(itemModel == null){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "商品信息不存在");
        }

        // // UserModel userModel = userService.getUserById(userId);
        // UserModel userModel = userService.getUserByIdInCache(userId);
        // if(userModel == null){
        //     throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "用户信息不存在");
        // }
        if(amount < 0 || amount > 99){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "数量信息不正确");
        }

        //校验活动信息
        // if(promoId != null){
        //     //（1）校验对应活动是否存在这个适用商品
        //     if(promoId.intValue() != itemModel.getPromoModel().getId()){
        //         throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"活动信息不正确");
        //         //（2）校验活动是否正在进行中
        //     }else if(itemModel.getPromoModel().getStatus().intValue() != 2) {
        //         throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"活动信息还未开始");
        //     }
        // }

        //2.落单减库存
        boolean result = itemService.decreaseStock(itemId, amount);
        if(!result){
            throw new BusinessException(EmBusinessError.STOCK_NOT_ENOUGH);
        }
        //3.订单入库
        OrderModel orderModel = new OrderModel();
        orderModel.setUserId(userId);
        orderModel.setAmount(amount);
        orderModel.setItemId(itemId);
        orderModel.setItemPrice(itemModel.getPrice());
        orderModel.setOrderPrice(itemModel.getPrice().multiply(new BigDecimal(amount)));
        //生成交易流水号,订单号
        orderModel.setId(generateOrderNo());
        OrderDO orderDO = this.convertFromOrderModel(orderModel);
        orderDOMapper.insertSelective(orderDO);
        // 增加商品销量
        itemService.increaseSales(itemId, amount);

        // 设置库存流水状态为成功
        StockLogDO stockLogDO = stockLogDOMapper.selectByPrimaryKey(stockLogId);
        if(stockLogDO == null){
            throw new BusinessException(EmBusinessError.UNKNOWN_ERROR);
        }
        stockLogDO.setStatus(2);
        stockLogDOMapper.updateByPrimaryKey(stockLogDO);

        // 采用事务型消息队列，不需要在事务完成之后发送消息
        // TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
        //     @Override
        //     public void afterCommit() {
        //         // 异步更新库存
        //         boolean mqResult = itemService.asyncDecreaseStock(itemId, amount);
        //         // if(!mqResult){
        //         //     itemService.increaseStock(itemId, amount);
        //         //     throw new BusinessException(EmBusinessError.MQ_SEND_FAIL);
        //         // }
        //     }
        // });

        //4.返回前端
        return orderModel;
    }

    @SuppressWarnings("all")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private String generateOrderNo(){
        // 订单16位
        StringBuilder sb = new StringBuilder();
        // 前8位为时间信息，年月日
        LocalDateTime now = LocalDateTime.now();
        String nowDate = now.format(DateTimeFormatter.ISO_DATE).replace("-", "");
        sb.append(nowDate);
        //中间6位为自增序列
        //获取当前sequence
        int sequence = 0;
        SequenceDO sequenceDO = sequenceDOMapper.getSequenceByName("order_info");
        sequence = sequenceDO.getCurrentValue();
        sequenceDO.setCurrentValue(sequenceDO.getCurrentValue() + sequenceDO.getStep());
        sequenceDOMapper.updateByPrimaryKeySelective(sequenceDO);
        String sequenceStr = String.valueOf(sequence);
        for(int i = 0; i < 6 - sequenceStr.length(); i++){
            sb.append(0);
        }
        sb.append(sequenceStr);
        //最后2位为分库分表位,暂时写死
        sb.append("00");
        return sb.toString();
    }

    private OrderDO convertFromOrderModel(OrderModel orderModel){
        if(orderModel == null){
            return null;
        }
        OrderDO orderDO = new OrderDO();
        BeanUtils.copyProperties(orderModel,orderDO);
        orderDO.setItemPrice(orderModel.getItemPrice().doubleValue());
        orderDO.setOrderPrice(orderModel.getOrderPrice().doubleValue());
        return orderDO;
    }
}
