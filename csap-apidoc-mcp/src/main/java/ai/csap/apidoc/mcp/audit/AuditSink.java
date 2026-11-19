package ai.csap.apidoc.mcp.audit;

/**
 * 审计记录的落库目标(内存环形缓冲之外的持久化)。实现必须不阻塞调用线程。
 *
 * @author ycf
 * @since 1.5.0
 */
public interface AuditSink extends AutoCloseable {

    /**
     * 提交一条记录;异步写入,队列满时丢弃。
     *
     * @param entry 记录
     */
    void offer(AuditEntry entry);

    /**
     * 落库位置的描述,用于日志
     */
    String describe();

    @Override
    void close();
}
