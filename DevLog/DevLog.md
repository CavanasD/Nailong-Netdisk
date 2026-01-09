\=========================

2026/1/8
后端完成基础登录接口

\=========================

2026/1/9

[🔨 | 开发]前端使用VUE实现简单登录注册，包括密码强度需要，邮箱用户名合法性验证

[☣ | 漏洞设计]

## 逻辑漏洞
[漏洞描述] ：在网盘系统的“修改用户信息”功能中，Dev设计一个典型的**水平越权** 漏洞。该漏洞允许任何已登录的攻击者，通过构造恶意请求，修改系统中其他任意用户的个人信息（如邮箱地址）。
[漏洞位置] ：后端 UserController 的 updateUser 方法。
[漏洞分析] ：后端在处理更新请求时，完全信任了前端提交的userId，而未校验该userId是否与当前操作者（通过Token识别）的身份一致。
2. 漏洞代码分析
我们首先在 UserController.java 中引入了以下有漏洞的接口：
```java
// ... in UserController.java  
@PutMapping("/update")  
public Result<Void> updateUser(@RequestBody UserUpdateDTO userUpdateDTO) {  
    // 漏洞所在：没有校验操作者身份，直接根据传入的 userId 进行更新  
    User userToUpdate = new User();  
    userToUpdate.setUserId(userUpdateDTO.getUserId()); // 完全信任了请求中的 userId    userToUpdate.setEmail(userUpdateDTO.getEmail());  
  
    boolean success = userService.updateById(userToUpdate);  
  
    if (success) {  
        return Result.success();  
    } else {  
        return Result.error("更新失败，用户可能不存在");  
    }  
}
```
如上所示，该方法直接从请求体 userUpdateDTO 中获取 userId 并执行数据库更新。正确的逻辑应该是从当前会话（即解析Token）中获取用户ID，并强制使用该ID进行更新，忽略请求体中传入的任何ID

同时前端引入缺陷功能：
```js
// ... in Home.vue  
const updateEmail = async () => {  
  // ...  
  try {  
    // 这里是关键，我们把当前用户的 userId 和新 email 发给后端  
    await request.put('/user/update', {  
      userId: user.value.userId, // 将当前用户的ID发往后端  
      email: newEmail.value  
    })  
    ElMessage.success('邮箱更新成功！')  
    await fetchUser()  
  } catch (e) {  
    // ...  
  }  
}
```

前端UI会显示当前用户的ID，并在更新时将此ID作为参数提交。这为攻击者提供了发现和利用漏洞的入口。
[漏洞验证] ：![[attachments/Pasted image 20260109221048.png]]
修改POST请求，成功验证
![[attachments/Pasted image 20260109221142.png]]

\=========================