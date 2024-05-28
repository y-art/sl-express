package com.sl.sdn.repository.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import com.sl.sdn.dto.OrganDTO;
import com.sl.sdn.dto.TransportLineNodeDTO;
import com.sl.sdn.entity.AgencyEntity;
import com.sl.sdn.enums.OrganTypeEnum;
import com.sl.sdn.repository.TransportLineRepository;
import org.neo4j.driver.internal.value.PathValue;
import org.neo4j.driver.types.Path;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;
import java.util.Optional;

@Component
public class TransportLineRepositoryImpl implements TransportLineRepository {

    @Resource
    private Neo4jClient neo4jClient;

    @Override
    public TransportLineNodeDTO findShortestPath(AgencyEntity start, AgencyEntity end) {
        //获取网点数据在Neo4j中的类型
        String type = AgencyEntity.class.getAnnotation(Node.class).value()[0];
        //构造查询语句
        String cypherQuery = StrUtil.format("MATCH path = shortestPath((start:{}) -[*..10]-> (end:{}))\n" +
                "WHERE start.bid = $startId AND end.bid = $endId \n" +
                "RETURN path", type, type);

        //执行查询
        Optional<TransportLineNodeDTO> optional = this.neo4jClient.query(cypherQuery)
                .bind(start.getBid()).to("startId") //设置参数
                .bind(end.getBid()).to("endId")//设置参数
                .fetchAs(TransportLineNodeDTO.class) //设置响应数据类型
                .mappedBy((typeSystem, record) -> { //对结果进行封装处理
                    PathValue pathValue = (PathValue) record.get(0);
                    Path path = pathValue.asPath();
                    TransportLineNodeDTO dto = new TransportLineNodeDTO();
                    // 读取节点数据
                    path.nodes().forEach(node -> {
                        Map<String, Object> map = node.asMap();
                        OrganDTO organDTO = BeanUtil.toBeanIgnoreError(map, OrganDTO.class);

                        //取第一个标签作为类型
                        organDTO.setType(OrganTypeEnum.valueOf(CollUtil.getFirst(node.labels())).getCode());
                        //查询出来的数据，x：经度，y：纬度
                        organDTO.setLatitude(BeanUtil.getProperty(map.get("location"), "y"));
                        organDTO.setLongitude(BeanUtil.getProperty(map.get("location"), "x"));

                        dto.getNodeList().add(organDTO);
                    });

                    //提取关系中的 cost 数据，进行求和计算，算出该路线的总成本
                    path.relationships().forEach(relationship -> {
                        Map<String, Object> objectMap = relationship.asMap();
                        double cost = Convert.toDouble(objectMap.get("cost"), 0d);
                        dto.setCost(NumberUtil.add(cost, dto.getCost().doubleValue()));
                    });

                    //取2位小数
                    dto.setCost(NumberUtil.round(dto.getCost(), 2).doubleValue());
                    return dto;
                }).one();

        return optional.orElse(null);
    }
}
