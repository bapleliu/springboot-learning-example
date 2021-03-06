package org.spring.springboot.service.impl;

import org.elasticsearch.action.admin.indices.analyze.AnalyzeAction;
import org.elasticsearch.action.admin.indices.analyze.AnalyzeRequestBuilder;
import org.elasticsearch.action.admin.indices.analyze.AnalyzeResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spring.springboot.domain.City;
import org.spring.springboot.repository.CityRepository;
import org.spring.springboot.service.CityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.core.query.SearchQuery;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 城市 ES 业务逻辑实现类
 * <p>
 * Created by bysocket on 07/02/2017.
 */
@Service
public class CityESServiceImpl implements CityService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CityESServiceImpl.class);

    @Autowired
    CityRepository cityRepository;

    @Autowired
    private ElasticsearchTemplate elasticsearchTemplate;

    /**
     * 调用 ES 获取 IK 分词后结果
     *
     * @param searchContent
     * @return
     */
    @Override
    public List<String> getIkAnalyzeSearchTerms(String searchContent) {
        // 调用 IK 分词分词
        AnalyzeRequestBuilder ikRequest = new AnalyzeRequestBuilder(elasticsearchTemplate.getClient(),
                AnalyzeAction.INSTANCE, "cityindex", searchContent);//
        ikRequest.setTokenizer("ik_max_word");//
        List<AnalyzeResponse.AnalyzeToken> ikTokenList = ikRequest.execute().actionGet().getTokens();

        // 循环赋值
        List<String> searchTermList = new ArrayList<>();
        ikTokenList.forEach(ikToken -> {
            searchTermList.add(ikToken.getTerm());
        });

        return searchTermList;
    }

    @Override
    public Long saveCity(City city) {

        City cityResult = cityRepository.save(city);
        return cityResult.getId();
    }

    @Override
    public List<City> searchCity(Integer pageNumber,
                                 Integer pageSize,
                                 String searchContent) {
        // 分页参数
        Pageable pageable = new PageRequest(pageNumber, pageSize);

//        // Function Score Query
        //方式1 老版本
//        FunctionScoreQueryBuilder functionScoreQueryBuilder = QueryBuilders.functionScoreQuery()
//                .add(QueryBuilders.boolQuery().should(QueryBuilders.matchQuery("cityname", searchContent)),
//                    ScoreFunctionBuilders.weightFactorFunction(1000))
//                .add(QueryBuilders.boolQuery().should(QueryBuilders.matchQuery("description", searchContent)),
//                        ScoreFunctionBuilders.weightFactorFunction(100));

        //方式2
        FunctionScoreQueryBuilder.FilterFunctionBuilder[] functions = {
                new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                        QueryBuilders.matchQuery("cityname", searchContent),
                        ScoreFunctionBuilders.weightFactorFunction(1000)),
                new FunctionScoreQueryBuilder.FilterFunctionBuilder(
                        QueryBuilders.matchQuery("description", searchContent),
                        ScoreFunctionBuilders.weightFactorFunction(100))
        };
        QueryBuilder functionScoreQueryBuilder = QueryBuilders.functionScoreQuery(functions);

        //方式3
//        FunctionScoreQueryBuilder functionScoreQueryBuilder =
//                QueryBuilders.functionScoreQuery(
//                        QueryBuilders.boolQuery().should(QueryBuilders.matchQuery("cityname", searchContent))
//                                .should(QueryBuilders.matchQuery("description", searchContent))
//                        , ScoreFunctionBuilders.weightFactorFunction(100));

        // 创建搜索 DSL 查询
        SearchQuery searchQuery = new NativeSearchQueryBuilder()
                .withPageable(pageable)
                .withQuery(functionScoreQueryBuilder).build();

        LOGGER.info("\n searchCity(): searchContent [" + searchContent + "] \n DSL  = \n " + searchQuery.getQuery().toString());

        Page<City> searchPageResults = cityRepository.search(searchQuery);
        return searchPageResults.getContent();
    }

}
