package org.buaa.semantic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 紧凑保存初值，不按数组长度分配宿主内存。null 元素代表需运行时求值的表达式。
 * zeroFill 指示未显式初始化的元素置零；普通未初始化局部对象的值保持未知。
 */
public record InitialValue(int length, List<Integer> explicitValues, boolean zeroFill) {
    public InitialValue {
        explicitValues = Collections.unmodifiableList(new ArrayList<>(explicitValues));
    }

    public Integer valueAt(int index) {
        if (index < 0 || index >= length) {
            return null;
        }
        if (index < explicitValues.size()) {
            return explicitValues.get(index);
        }
        return zeroFill ? Integer.valueOf(0) : null;
    }
}
