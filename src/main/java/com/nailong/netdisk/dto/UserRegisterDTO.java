package com.nailong.netdisk.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Data
public class UserRegisterDTO {
    @NotEmpty(message = "用户名不能为空")
    @Length(min = 4, max = 20, message = "用户名长度需在4-20字符之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")

    private String username;

    @NotEmpty(message = "密码不能为空")
    @Length(min = 6, max = 20, message = "密码长度需在6-20字符之间")
    //密码强度一定要够✍✍✍
    @Pattern(regexp = "^(?:(?=.*[a-z])(?=.*[A-Z])|(?=.*[a-z])(?=.*\\d)|(?=.*[a-z])(?=.*[^a-zA-Z0-9])|(?=.*[A-Z])(?=.*\\d)|(?=.*[A-Z])(?=.*[^a-zA-Z0-9])|(?=.*\\d)(?=.*[^a-zA-Z0-9])).{6,20}$",
            message = "密码强度不足！请包含大小写字母、数字和特殊字符中任意二者以上的组合")
    private String password;

    @NotEmpty(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;
}

