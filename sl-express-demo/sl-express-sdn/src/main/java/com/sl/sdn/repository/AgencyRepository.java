package com.sl.sdn.repository;

import com.sl.sdn.entity.AgencyEntity;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface AgencyRepository extends Neo4jRepository<AgencyEntity,Long> {

    /**
     * 自定义条件查询
     * */
    //根据bid查询网点数据
    AgencyEntity findByBid(Long bid);
}
