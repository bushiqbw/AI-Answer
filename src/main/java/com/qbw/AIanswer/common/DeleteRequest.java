package com.qbw.AIanswer.common;

import java.io.Serializable;
import lombok.Data;

/**
 * 删除请求
 *
 * @author <a href="https://github.com/bushiqbw">qbw</a>
 * @from <a href="https://github.com/bushiqbw/springboot-init-pure">springboot-init-pure</a>
 */
@Data
public class DeleteRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    private static final long serialVersionUID = 1L;
}