package com.github.technus.painlessMesh.mesh;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.Map;

@Data
@Accessors(chain = true)
public class Node {
    protected final long            nodeId;
    protected final boolean         root;
    protected       Map<Long, Node> children = new HashMap<>();

    protected void putChild(Node childNode) {
        getChildren().put(childNode.getNodeId(), childNode);
    }
}
