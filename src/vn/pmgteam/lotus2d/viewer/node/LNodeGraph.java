package vn.pmgteam.lotus2d.viewer.node;

import java.util.HashMap;
import java.util.Map;

public class LNodeGraph {
    // Logic quản lý ID từ class Graph của bạn
    public int nextNodeId = 1;
    public int nextPinId = 100;

    // Danh sách các Node trong Graph
    public final Map<Integer, AccessoryNode> nodes = new HashMap<>();

    public LNodeGraph() {
        // Khởi tạo mặc định nếu cần cho modcoderpack-redevelop
    }

    /**
     * Tạo Node mới và tự động gán Pin ID theo logic class Graph
     */
    public AccessoryNode createNode(String name) {
        AccessoryNode node = new AccessoryNode(nextNodeId++, nextPinId++, nextPinId++, name);
        this.nodes.put(node.nodeId, node);
        return node;
    }

    /**
     * Tìm Node dựa trên ID của Pin Đầu vào (Input)
     */
    public AccessoryNode findByInput(long inputPinId) {
        for (AccessoryNode node : nodes.values()) {
            if (node.inputPinId == inputPinId) return node;
        }
        return null;
    }

    /**
     * Tìm Node dựa trên ID của Pin Đầu ra (Output)
     */
    public AccessoryNode findByOutput(long outputPinId) {
        for (AccessoryNode node : nodes.values()) {
            if (node.outputPinId == outputPinId) return node;
        }
        return null;
    }

    // Inner class thay thế cho GraphNode
    public static final class AccessoryNode {
        public final int nodeId;
        public final int inputPinId;
        public final int outputPinId;
        public String name;

        // Lưu ID của Node tiếp theo mà nó trỏ tới (Logic liên kết)
        public int outputNodeId = -1;

        public AccessoryNode(int nodeId, int inputPinId, int outputPinId, String name) {
            this.nodeId = nodeId;
            this.inputPinId = inputPinId;
            this.outputPinId = outputPinId;
            this.name = name;
        }

        public String getName() {
            return name + " (" + (char) (64 + nodeId) + ")";
        }
    }
}