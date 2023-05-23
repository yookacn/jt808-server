package org.yzh.web.service;

import com.alibaba.fastjson.JSON;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yzh.protocol.basics.JTMessage;

import javax.annotation.Resource;

@Component
public class MsgTransferService {

    private static final Logger log = LoggerFactory.getLogger(MsgTransferService.class);

    private static final String F_OUT_DATA = "iotFanoutExchangeData";
    private static final String F_OUT_STATUS = "iotFanoutExchangeStatus";
    private static final String F_OUT_EVENT = "iotFanoutExchangeEvent";

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    public void sendData(JTMessage message) {
        sendMessage(message, F_OUT_DATA);
    }

    public void sendStatus(JTMessage message) {
        sendMessage(message, F_OUT_STATUS);
    }

    public void sendEvent(JTMessage message) {
        sendMessage(message, F_OUT_EVENT);
    }

    private void sendMessage(JTMessage message, String topic) {
        String jsonMsg = JSON.toJSONString(message);
        SendResult send = rocketMQTemplate.syncSend(topic, jsonMsg);
        if(!send.getSendStatus().equals(SendStatus.SEND_OK)) {
            log.warn("数据转发未成功！{}", jsonMsg);
        }
    }
}
