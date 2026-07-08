package com.lawai.legalassistant.modules.casep.service;

import com.lawai.legalassistant.ai.client.AgnesClient;
import com.lawai.legalassistant.modules.casep.dto.CaseSearchRequest;
import com.lawai.legalassistant.modules.casep.dto.CaseVO;
import com.lawai.legalassistant.modules.casep.mapper.CaseMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 案例检索服务
 * <p>
 * 提供基于关键词的向量检索 + 元数据过滤，以及无关键词时的元数据分页检索，
 * 对应设计方案 5.4 节案例专业检索。
 */
@Service
public class CaseService {

    private static final Logger log = LoggerFactory.getLogger(CaseService.class);

    /** 向量检索默认 Top-K */
    private static final int VECTOR_TOP_K = 20;

    private final CaseMapper caseMapper;
    private final AgnesClient agnesClient;

    public CaseService(CaseMapper caseMapper, AgnesClient agnesClient) {
        this.caseMapper = caseMapper;
        this.agnesClient = agnesClient;
    }

    /**
     * 案例检索
     * <p>
     * - 有关键词：关键词向量化 → pgvector 余弦相似检索 + 元数据过滤，返回 Top-K
     * - 无关键词：按元数据条件分页查询
     *
     * @param req 检索请求
     * @return 案例列表
     */
    public List<CaseVO> search(CaseSearchRequest req) {
        if (req == null) {
            req = new CaseSearchRequest();
        }

        boolean hasKeyword = req.getKeyword() != null && !req.getKeyword().isBlank();

        if (hasKeyword) {
            return searchByVector(req);
        } else {
            return searchByMetadata(req);
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 向量检索 + 元数据过滤
     */
    private List<CaseVO> searchByVector(CaseSearchRequest req) {
        try {
            float[] vec = agnesClient.embed(req.getKeyword());
            String vecStr = toPgVector(vec);
            int topK = VECTOR_TOP_K;
            if (req.getSize() != null && req.getSize() > 0) {
                topK = Math.min(req.getSize() * 2, VECTOR_TOP_K);
            }
            return caseMapper.searchByVectorWithFilters(
                    vecStr, req.getCause(), req.getCourtLevel(), req.getYear(), topK);
        } catch (Exception e) {
            log.error("案例向量检索失败: keyword={}", req.getKeyword(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 元数据分页检索
     */
    private List<CaseVO> searchByMetadata(CaseSearchRequest req) {
        int page = req.getPage() != null && req.getPage() > 0 ? req.getPage() : 1;
        int size = req.getSize() != null && req.getSize() > 0 ? req.getSize() : 10;
        int offset = (page - 1) * size;
        return caseMapper.searchByMetadata(
                req.getCause(), req.getCourtLevel(), req.getYear(), offset, size);
    }

    /**
     * float[] → pgvector 字符串 [v1,v2,...]
     */
    private String toPgVector(float[] vec) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(vec[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
