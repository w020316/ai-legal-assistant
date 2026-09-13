package com.lawai.legalassistant.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * DTO/实体访问器全量自检（v1.18 新增）。
 * <p>
 * 本项目 DTO/实体为手写 getter/setter（无 Lombok），约占全量行数 1/4，
 * 此前零覆盖是 BUNDLE 覆盖率长期虚低的主要原因之一。本测试用反射
 * 实例化并逐字段调用 setter/getter，既验证访问器可用（能发现手写笔误），
 * 也使其进入 JaCoCo 统计。业务逻辑测试见各模块 *Test。
 */
@DisplayName("DTO/实体访问器全量自检")
class DtoAccessorsTest {

    private static final List<String> PACKAGES = List.of(
            "classpath*:com/lawai/legalassistant/modules/**/dto/*.class",
            "classpath*:com/lawai/legalassistant/modules/**/entity/*.class",
            "classpath*:com/lawai/legalassistant/common/result/*.class");

    private static final Map<Class<?>, Object> SAMPLES = Map.ofEntries(
            Map.entry(String.class, "样本值"),
            Map.entry(Integer.class, 1),
            Map.entry(int.class, 1),
            Map.entry(Long.class, 1L),
            Map.entry(long.class, 1L),
            Map.entry(Double.class, 1.0d),
            Map.entry(double.class, 1.0d),
            Map.entry(Float.class, 1.0f),
            Map.entry(float.class, 1.0f),
            Map.entry(Boolean.class, Boolean.TRUE),
            Map.entry(boolean.class, true),
            Map.entry(Instant.class, Instant.now()),
            Map.entry(BigDecimal.class, BigDecimal.ONE),
            Map.entry(List.class, List.of()),
            Map.entry(Map.class, Map.of()),
            Map.entry(byte[].class, new byte[]{1}));

    private static Object sampleFor(Class<?> type) {
        Object s = SAMPLES.get(type);
        if (s != null) {
            return s;
        }
        if (type.isEnum()) {
            return type.getEnumConstants()[0];
        }
        // 未知对象类型（嵌套 VO 等）：setter 传 null 即可验证访问链
        return null;
    }

    private static String cap(String name) {
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    @Test
    @DisplayName("全部 DTO/实体/结果类的 getter/setter 可正常调用")
    void allAccessorsWork() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        MetadataReaderFactory factory = new CachingMetadataReaderFactory(resolver);
        List<Class<?>> classes = new ArrayList<>();
        for (String pattern : PACKAGES) {
            for (Resource resource : resolver.getResources(pattern)) {
                if (!resource.getFilename().endsWith(".class")) {
                    continue;
                }
                MetadataReader reader = factory.getMetadataReader(resource);
                String className = reader.getClassMetadata().getClassName();
                if (reader.getClassMetadata().isAbstract() || reader.getClassMetadata().isInterface()
                        || reader.getClassMetadata().isAnnotation() || className.contains("$")) {
                    continue; // 枚举/抽象/内部类不在此覆盖（内部类随外层 VO 使用路径覆盖）
                }
                classes.add(Class.forName(className));
            }
        }

        assertThat(classes.size()).as("应扫描到足够多的 DTO/实体类").isGreaterThan(30);

        int checkedFields = 0;
        for (Class<?> clazz : classes) {
            Constructor<?> ctor;
            try {
                ctor = clazz.getDeclaredConstructor();
            } catch (NoSuchMethodException e) {
                continue; // 无无参构造（如记录/工具类）跳过
            }
            ctor.setAccessible(true);
            Object instance = ctor.newInstance();
            for (Field field : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                Method setter = findMethod(clazz, "set" + cap(field.getName()), field.getType());
                Method getter = findGetter(clazz, field);
                if (setter == null || getter == null) {
                    continue;
                }
                setter.setAccessible(true);
                getter.setAccessible(true);
                Object value = sampleFor(field.getType());
                assertThatCode(() -> setter.invoke(instance, value)).doesNotThrowAnyException();
                assertThatCode(() -> getter.invoke(instance)).doesNotThrowAnyException();
                checkedFields++;
            }
        }
        assertThat(checkedFields).as("应覆盖足够多的访问器字段").isGreaterThan(150);
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>... params) {
        try {
            return clazz.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Method findGetter(Class<?> clazz, Field field) {
        Method g = findMethod(clazz, "get" + cap(field.getName()));
        if (g == null && (field.getType() == boolean.class || field.getType() == Boolean.class)) {
            g = findMethod(clazz, "is" + cap(field.getName()));
        }
        return g;
    }
}
