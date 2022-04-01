package com.github.technus.painlessMesh.mesh;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Accessors(chain = true)
public class Mesh {
    protected Map<Long,Node> nodes =new HashMap<>();
    protected Node           root;

    public void setRootNode(long id, List<Long> children){
        getNodes().clear();
        setRoot(new Node(id,true));
        putNode(getRoot());
        putChildren(getRoot(),children);
    }

    public void putNode(long id,List<Long> children){
        Node node=getNodes().get(id);
        putChildren(node,children);
    }

    protected void putNode(Node childNode) {
        getNodes().put(childNode.getNodeId(),childNode);
    }

    protected void putChildren(Node parentNode, List<Long> children){
        children.forEach(child-> {
            Node childNode=new Node(child,false);
            parentNode.putChild(childNode);
            putNode(childNode);
        });
    }
}
