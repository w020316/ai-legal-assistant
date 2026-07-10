package com.lawai.legalassistant.modules.chat.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public class BatchDeleteRequest {
    @NotEmpty(message = "请选择要删除的会话")
    @Size(max = 50, message = "单次最多删除 50 条")
    private List<Long> ids;

    public List<Long> getIds() { return ids; }
    public void setIds(List<Long> ids) { this.ids = ids; }
}
