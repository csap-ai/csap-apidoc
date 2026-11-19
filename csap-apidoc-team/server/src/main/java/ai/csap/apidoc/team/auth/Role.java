package ai.csap.apidoc.team.auth;

/** 项目角色,rank 越大权限越高。 */
public enum Role {
    VIEWER(1), DEVELOPER(2), MAINTAINER(3);

    private final int rank;

    Role(int rank) {
        this.rank = rank;
    }

    public boolean atLeast(Role other) {
        return rank >= other.rank;
    }

    public static Role parse(String s) {
        try {
            return s == null ? null : valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
