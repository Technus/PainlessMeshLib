package com.github.technus.painlessMesh.json.packet;

import com.github.technus.painlessMesh.json.Type;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.util.Optional;

@Data
@Accessors(chain = true)
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class Packet extends Type {
    protected long dest;//uint32_t
    protected long from;//uint32_t

    public Optional<Integer> msgType() {
        return Optional.empty();
    }
}
