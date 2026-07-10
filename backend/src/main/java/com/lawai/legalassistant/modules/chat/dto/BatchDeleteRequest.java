package com.lawai.legalassistant.modules.chat.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class BatchDeleteRequest {
    @NotEmpty(message = "请选择要删除的会话")
    private List<Long> ids;

    public List<Long> getIds() { return ids; }
    public void setIds(List<Long> ids) { this.ids = ids; }
}
