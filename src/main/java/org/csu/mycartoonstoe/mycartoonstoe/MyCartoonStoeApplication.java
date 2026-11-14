package org.csu.mycartoonstoe.mycartoonstoe;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// DemoController、User、UserMapper 位于 org.csu.mypetstore 包下，不在本类所在包的子包内，
// 需显式扩大组件扫描范围，否则控制器不会被注册为 Bean；
// Mapper 接口不会被组件扫描收录，另需 @MapperScan 指定 mapper 包，
// 因为 MyBatis-Plus 默认的自动扫描只覆盖本类所在的包
@SpringBootApplication(scanBasePackages = {"org.csu.mycartoonstoe", "org.csu.mypetstore"})
@MapperScan("org.csu.mypetstore.mapper")
public class MyCartoonStoeApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyCartoonStoeApplication.class, args);
    }

}
