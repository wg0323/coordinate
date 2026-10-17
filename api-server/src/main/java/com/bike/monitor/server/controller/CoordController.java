package com.bike.monitor.server.controller;

import com.bike.monitor.common.geo.CoordConverter;
import com.bike.monitor.server.common.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 坐标转换接口（题 4）：同一坐标三系对比。
 */
@RestController
@RequestMapping("/api/coord")
@Tag(name = "坐标转换")
public class CoordController {

    /** WGS84 坐标一键输出三系结果（入库前统一 WGS84 的演示对照） */
    @Operation(summary = "三系坐标转换", description = "同一坐标 WGS84/GCJ02/BD09 对比（题 4）")
    @GetMapping("/convert")
    public ApiResult<Map<String, Object>> convert(@RequestParam double lng, @RequestParam double lat,
                                                  @RequestParam(defaultValue = "wgs84") String from,
                                                  @RequestParam(defaultValue = "wgs84") String to) {
        Map<String, Object> data = new LinkedHashMap<>();
        if ("wgs84".equalsIgnoreCase(from)) {
            data.put("wgs84", new double[]{lng, lat});
            data.put("gcj02", CoordConverter.wgs84ToGcj02(lng, lat));
            data.put("bd09", CoordConverter.wgs84ToBd09(lng, lat));
            return ApiResult.ok(data);
        }
        double[] out;
        switch (from.toLowerCase()) {
            case "gcj02":
                out = "bd09".equalsIgnoreCase(to)
                        ? CoordConverter.gcj02ToBd09(lng, lat)
                        : CoordConverter.gcj02ToWgs84(lng, lat);
                break;
            case "bd09":
                out = "gcj02".equalsIgnoreCase(to)
                        ? CoordConverter.bd09ToGcj02(lng, lat)
                        : CoordConverter.bd09ToWgs84(lng, lat);
                break;
            default:
                return ApiResult.error("不支持的源坐标系: " + from);
        }
        data.put(to.toLowerCase(), out);
        return ApiResult.ok(data);
    }
}
