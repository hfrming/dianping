package com.hmdp.utils;

public interface ILock {

    //尝试获取锁
    public boolean tryLock(Long timeoutSec);

    //释放锁
    public void unLock();
}
