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
public final class PageResult<T> {
  private final long total;
  private final int page;
  private final int size;
  private final List<T> items;

  public PageResult(long total, int page, int size, List<T> items) {
    this.total = total;
    this.page = page;
    this.size = size;
    this.items = items;
  }

  public long total() {
    return total;
  }

  public int page() {
    return page;
  }

  public int size() {
    return size;
  }

  public List<T> items() {
    return items;
  }

  public long getTotal() {
    return total;
  }

  public int getPage() {
    return page;
  }

  public int getSize() {
    return size;
  }

  public List<T> getItems() {
    return items;
  }

  @Override
  public boolean equals(Object value) {
    if (this == value) return true;
    if (!(value instanceof PageResult)) return false;
    PageResult<?> other = (PageResult<?>) value;
    return total == other.total
        && page == other.page
        && size == other.size
        && java.util.Objects.equals(items, other.items);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(total, page, size, items);
  }

  @Override
  public String toString() {
    return "PageResult{"
        + "total="
        + total
        + ", page="
        + page
        + ", size="
        + size
        + ", items="
        + items
        + "}";
  }
}
