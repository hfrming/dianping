package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    StringRedisTemplate redisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //判断手机号是否合理
        if (RegexUtils.isPhoneInvalid(phone)) {
           //不合理则失败
           return Result.fail("手机号不合法！");
        }
        //生成验证码
        String code = RandomUtil.randomNumbers(6);
        //保存session
        //session.setAttribute("code", code);
        //session.setAttribute("phone", phone);

        //保存到redis
        redisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //发送验证码
        log.debug("发送验证码成功，{}，{}", phone, code);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //获取session中的手机号和验证码，判断是否一致
        //String phone = (String) session.getAttribute("phone");
        //String code = (String) session.getAttribute("code");
        // if(!loginForm.getPhone().equals(phone) || !loginForm.getCode().equals(code)) {
        //    return Result.fail("创建失败");
        //}
        //从redis获取验证码并校验
        String code = redisTemplate.opsForValue().get(LOGIN_CODE_KEY + loginForm.getPhone());
        if (code == null || !code.equals(loginForm.getCode())) {
            return Result.fail("验证码错误");
        }
        String phone = loginForm.getPhone();
        //查询该用户是否存在
        User user = query().eq("phone", phone).one();
        if(user == null) {
            //不存在则新建
            user = createUserWithPhone(phone);
        }
        //session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));

        //生成token
        String token = UUID.randomUUID(true).toString();
        //将User转为Map
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true)
                        .setFieldValueEditor((filedKey, filedVal) -> filedVal.toString()));
        //存入redis
        redisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);
        //设置有效期
        redisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);

        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
