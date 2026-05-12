package com.yk.demoai.mapper;

import com.yk.demoai.model.LogicalRelation;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface LogicalRelationMapper {

    @Select("SELECT * FROM logical_relation WHERE id = #{id} AND is_deleted = 0")
    LogicalRelation selectById(@Param("id") Integer id);

    @Select("SELECT * FROM logical_relation WHERE datasource_id = #{datasourceId} AND is_deleted = 0 ORDER BY created_time DESC")
    List<LogicalRelation> selectByDatasourceId(@Param("datasourceId") String datasourceId);

    @Insert("""
            INSERT INTO logical_relation
                (datasource_id, source_table_name, source_column_name, target_table_name, target_column_name,
                 relation_type, relation_category, description, is_deleted, created_time, updated_time)
            VALUES (#{datasourceId}, #{sourceTableName}, #{sourceColumnName}, #{targetTableName}, #{targetColumnName},
                    #{relationType}, #{relationCategory}, #{description}, 0, NOW(), NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(LogicalRelation logicalRelation);

    @Update("""
            <script>
            UPDATE logical_relation
            <set>
                <if test="sourceTableName != null">source_table_name = #{sourceTableName},</if>
                <if test="sourceColumnName != null">source_column_name = #{sourceColumnName},</if>
                <if test="targetTableName != null">target_table_name = #{targetTableName},</if>
                <if test="targetColumnName != null">target_column_name = #{targetColumnName},</if>
                <if test="relationType != null">relation_type = #{relationType},</if>
                <if test="relationCategory != null">relation_category = #{relationCategory},</if>
                <if test="description != null">description = #{description},</if>
                updated_time = NOW()
            </set>
            WHERE id = #{id}
            </script>
            """)
    int updateById(LogicalRelation logicalRelation);

    @Update("UPDATE logical_relation SET is_deleted = 1, updated_time = NOW() WHERE id = #{id}")
    int deleteById(@Param("id") Integer id);

    @Update("UPDATE logical_relation SET is_deleted = 1, updated_time = NOW() WHERE datasource_id = #{datasourceId}")
    int deleteByDatasourceId(@Param("datasourceId") String datasourceId);

    @Select("""
            SELECT COUNT(*) FROM logical_relation
            WHERE datasource_id = #{datasourceId}
              AND source_table_name = #{sourceTableName}
              AND source_column_name = #{sourceColumnName}
              AND target_table_name = #{targetTableName}
              AND target_column_name = #{targetColumnName}
              AND is_deleted = 0
            """)
    int checkExists(@Param("datasourceId") String datasourceId, 
                    @Param("sourceTableName") String sourceTableName,
                    @Param("sourceColumnName") String sourceColumnName, 
                    @Param("targetTableName") String targetTableName,
                    @Param("targetColumnName") String targetColumnName);
}
