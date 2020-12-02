package io.github.jesse0722.speedkill.mysql;

/**
 * @author Lijiajun
 * @date 2020/11/29 22:04
 */
public class DynamicDataSourceHolder {

    private static final String MASTER = "master";

    private static final String SLAVE = "slave1";

    private static ThreadLocal<String> holder = new ThreadLocal<>();


    public static void putDataSourceKey(String key) {
        holder.set(key);
    }

    public static String getDataSourceKey() {
        return holder.get();
    }

    /**
     * 标记写库
     */
    public static void markMaster(){
        putDataSourceKey(MASTER);
    }

    /**
     * 标记读库
     */
    public static void markSlave(){
        putDataSourceKey(SLAVE);
    }
}
