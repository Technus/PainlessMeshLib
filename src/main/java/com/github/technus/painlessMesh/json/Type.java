package com.github.technus.painlessMesh.json;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public abstract class Type {
    protected int type;//int
}
