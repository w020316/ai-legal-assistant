package com.lawai.legalassistant.modules.user.service;

import com.lawai.legalassistant.common.exception.BusinessException;
import com.lawai.legalassistant.modules.rag.entity.KnowledgeDoc;
import com.lawai.legalassistant.modules.rag.mapper.KnowledgeChunkMapper;
import com.lawai.legalassistant.modules.rag.mapper.KnowledgeDocMapper;
import com.lawai.legalassistant.modules.rag.service.RagService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PersonalKnowledgeService} 单元测试（v1.18 新增，补覆盖率洼地）。
 * <p>
 * 覆盖上传（TXT 解析入 PRIVATE 库）、图片拦截、空文本拒绝、列表与删除越权。
 */
@DisplayName("PersonalKnowledgeService 个人知识库")
@ExtendWith(MockitoExtension.class)
class PersonalKnowledgeServiceTest {

    @Mock
    private RagService ragService;
    @Mock
    private KnowledgeDocMapper knowledgeDocMapper;
    @Mock
    private KnowledgeChunkMapper knowledgeChunkMapper;

    @org.junit.jupiter.api.BeforeAll
    static void initTableInfo() {
        // LambdaQueryWrapper 的 lambda 列名解析依赖 MP TableInfo 元数据，纯单测上下文需手动初始化
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                com.lawai.legalassistant.modules.rag.entity.KnowledgeDoc.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                com.lawai.legalassistant.modules.rag.entity.KnowledgeChunk.class);
    }

    private PersonalKnowledgeService service() {
        return new PersonalKnowledgeService(ragService, knowledgeDocMapper, knowledgeChunkMapper);
    }

    @Test
    @DisplayName("TXT 上传：解析文本入 PRIVATE 库")
    void uploadTxtIngestsPrivate() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "合同.txt", "text/plain", "这是一份合同正文内容".getBytes());
        when(ragService.ingestDocument(eq("合同.txt"), eq("USER_UPLOAD"), eq("个人上传"),
                anyString(), eq("PRIVATE"), eq(7L))).thenReturn(66L);

        Long docId = service().upload(7L, file);

        assertThat(docId).isEqualTo(66L);
    }

    @Test
    @DisplayName("空文件与图片类型拒绝")
    void uploadRejects() {
        assertThatThrownBy(() -> service().upload(7L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件不能为空");

        // JPEG magic bytes (FF D8 FF) + .jpg 扩展名 → FileTypeValidator 判定 JPG → 图片拦截
        MockMultipartFile image = new MockMultipartFile(
                "file", "照片.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00});
        assertThatThrownBy(() -> service().upload(7L, image))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("暂不支持图片");
    }

    @Test
    @DisplayName("列表：仅查当前用户的 PRIVATE 文档")
    void listOwnedPrivate() {
        when(knowledgeDocMapper.selectList(any())).thenReturn(List.of(new KnowledgeDoc()));
        assertThat(service().list(7L)).hasSize(1);
    }

    @Test
    @DisplayName("删除：归属校验通过后级联删切片")
    void deleteOwned() {
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setId(66L);
        doc.setOwnerType("PRIVATE");
        doc.setOwnerId(7L);
        when(knowledgeDocMapper.selectById(66L)).thenReturn(doc);

        service().delete(7L, 66L);

        verify(knowledgeChunkMapper).delete(any());
        verify(knowledgeDocMapper).deleteById(66L);
    }

    @Test
    @DisplayName("删除越权：非私有或非本人 → 404")
    void deleteForeignRejected() {
        KnowledgeDoc publicDoc = new KnowledgeDoc();
        publicDoc.setId(1L);
        publicDoc.setOwnerType("PUBLIC");
        publicDoc.setOwnerId(null);
        when(knowledgeDocMapper.selectById(1L)).thenReturn(publicDoc);

        assertThatThrownBy(() -> service().delete(7L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文档不存在或无权限");
    }
}
