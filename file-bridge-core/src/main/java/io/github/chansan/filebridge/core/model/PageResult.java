package io.github.chansan.filebridge.core.model;

import java.util.List;

/**
 * 分页数据容器。
 *
 * @param total 符合条件的全部记录总数
 * @param page 当前页码，从 1 开始
 * @param size 每页记录容量
 * @param items 当前页数据集
 * @param <T> 数据记录类型
 */
public record PageResult<T>(long total, int page, int size, List<T> items) {}
