package com.qbw.AIanswer.constant;

/**
 * 用户常量
 *
 * @author <a href="https://github.com/bushiqbw">qbw</a>
 * @from <a href="https://github.com/bushiqbw/springboot-init-pure">springboot-init-pure</a>
 */
public interface UserConstant {

    /**
     * 用户登录态键
     */
    String USER_LOGIN_STATE = "user_login";

    //  region 权限

    /**
     * 默认角色
     */
    String DEFAULT_ROLE = "user";

    /**
     * 管理员角色
     */
    String ADMIN_ROLE = "admin";

    /**
     * 被封号
     */
    String BAN_ROLE = "ban";

    // endregion

    String DEFAULT_AVATAR = "https://oj-qbw-1324037679.cos.ap-shanghai.myqcloud.com/OJ_Avatar.jpg";

    /**
     * 默认随机生成用户名
     */
    String DEFAULT_USERNAME = "做题家-"+String.valueOf(System.currentTimeMillis()).substring(0,5);
}
