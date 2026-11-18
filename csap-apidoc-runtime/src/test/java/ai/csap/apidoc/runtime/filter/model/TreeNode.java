package ai.csap.apidoc.runtime.filter.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 黄金样本用:递归结构
 */
public class TreeNode {
    private String name;
    private int level;
    private List<TreeNode> children = new ArrayList<>();

    public TreeNode() {
    }

    public static TreeNode of(int depth, int width) {
        TreeNode n = new TreeNode();
        n.name = "n" + depth;
        n.level = depth;
        if (depth > 0) {
            for (int i = 0; i < width; i++) {
                n.children.add(of(depth - 1, width));
            }
        }
        return n;
    }

    public String getName() {
        return name;
    }

    public int getLevel() {
        return level;
    }

    public List<TreeNode> getChildren() {
        return children;
    }
}
