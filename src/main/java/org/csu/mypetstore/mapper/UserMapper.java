package org.csu.mypetstore.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.csu.mypetstore.entity.User;

/**
 * store_user 的 Mapper：继承 BaseMapper 即获得单表 CRUD 能力，本实验不额外声明方法。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
