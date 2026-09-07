package com.dyc.xiaohashu.id.generator.dto.resp;

import com.dyc.xiaohashu.id.generator.enums.IdGeneratorType;

import java.util.List;

public class BatchGenerateIdRspDTO {

    private IdGeneratorType type;
    private int size;
    private List<Long> ids;

    public static Builder builder() {
        return new Builder();
    }

    public IdGeneratorType getType() {
        return type;
    }

    public void setType(IdGeneratorType type) {
        this.type = type;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public List<Long> getIds() {
        return ids;
    }

    public void setIds(List<Long> ids) {
        this.ids = ids;
    }

    public static class Builder {
        private final BatchGenerateIdRspDTO target = new BatchGenerateIdRspDTO();

        public Builder type(IdGeneratorType type) {
            target.setType(type);
            return this;
        }

        public Builder size(int size) {
            target.setSize(size);
            return this;
        }

        public Builder ids(List<Long> ids) {
            target.setIds(ids);
            return this;
        }

        public BatchGenerateIdRspDTO build() {
            return target;
        }
    }
}
