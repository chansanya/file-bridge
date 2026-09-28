package io.github.chansan.filebridge.core.spi;

import io.github.chansan.filebridge.core.model.ObjectLocation;
import io.github.chansan.filebridge.core.model.StorageCapabilities;
import io.github.chansan.filebridge.core.model.StoredObject;
import java.io.InputStream;
import java.util.Optional;

/** 物理对象存储的基础抽象，所有实现必须使用有界内存进行流式处理。 */
public interface StorageProvider {
  /**
   * @return 配置中稳定且唯一的存储实例 ID
   */
  String storageId();

  /**
   * @return 当前存储实例支持的能力和分片限制
   */
  StorageCapabilities capabilities();

  /**
   * 写入一个物理对象并计算可信摘要。
   *
   * @param request 服务端生成的对象路径、预期大小和内容类型
   * @param input 对象内容输入流
   * @return 已落盘或已被平台确认的对象元数据
   */
  StoredObject write(ObjectWriteRequest request, InputStream input);

  /**
   * 打开对象读取流。
   *
   * @param location 可信的对象定位信息
   * @return 对象输入流，调用方必须关闭
   */
  InputStream open(ObjectLocation location);

  /**
   * 查询对象元数据。
   *
   * @param location 可信的对象定位信息
   * @return 对象存在时返回元数据，否则返回空
   */
  Optional<StoredObject> stat(ObjectLocation location);

  /**
   * 幂等删除对象。
   *
   * @param location 可信的对象定位信息
   */
  void delete(ObjectLocation location);
}
