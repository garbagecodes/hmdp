package com.hmdp.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import java.util.HashMap;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class Event {

    private String topic;
    private Long userId;
    private Long entityId;
    private Map<String, Object> data = new HashMap<>();

}
