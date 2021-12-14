package org.yzh.web.controller;

import io.github.yezhihao.netmc.session.Session;
import io.github.yezhihao.netmc.session.SessionManager;
import io.github.yezhihao.protostar.MultiVersionSchemaManager;
import io.github.yezhihao.protostar.util.Explain;
import io.github.yezhihao.protostar.util.Info;
import io.github.yezhihao.protostar.util.StrUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.web.bind.annotation.*;
import org.yzh.commons.model.APIResult;
import org.yzh.commons.mybatis.Page;
import org.yzh.commons.mybatis.PageInfo;
import org.yzh.commons.mybatis.Pagination;
import org.yzh.commons.util.LogUtils;
import org.yzh.protocol.codec.JTMessageDecoder;
import org.yzh.protocol.codec.MultiPacketDecoder;
import org.yzh.web.config.WebLogAdapter;
import org.yzh.web.model.enums.SessionKey;
import org.yzh.web.model.vo.DeviceInfo;
import org.yzh.web.model.vo.DeviceQuery;
import org.yzh.web.model.vo.Location;
import org.yzh.web.model.vo.LocationQuery;
import org.yzh.web.service.LocationService;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping
public class OtherController {

    private final LocationService locationService;

    private final SessionManager sessionManager;

    private final JTMessageDecoder decoder;

    public OtherController(LocationService locationService, SessionManager sessionManager, MultiVersionSchemaManager schemaManager) {
        this.locationService = locationService;
        this.sessionManager = sessionManager;
        this.decoder = new MultiPacketDecoder(schemaManager);
    }

    @Hidden
    @Operation(hidden = true)
    @GetMapping
    public void doc(HttpServletResponse response) throws IOException {
        response.sendRedirect("doc.html");
    }

    @Operation(summary = "终端实时信息查询")
    @GetMapping("terminal/all")
    public Pagination<Session> all(DeviceQuery query) {
        Collection<Session> all = sessionManager.all();
        Stream<Session> stream = all.stream();

        if (!query.isEmpty()) {
            all = all.stream().filter(query).collect(Collectors.toList());
            stream = all.stream();
        }

        List<Session> page = stream
                .skip(query.offset())
                .limit(query.getLimit())
                .collect(Collectors.toList());

        query.setCount(all.size());
        return new Pagination<>(query, page);
    }

    @Operation(summary = "获得当前所有在线设备信息")
    @GetMapping("terminal/option")
    public APIResult<List<String>> getClientId() {
        Collection<Session> all = sessionManager.all();
        List<String> result = all.stream().map(Session::getId).collect(Collectors.toList());
        return APIResult.ok(result);
    }

    @Operation(summary = "websocket订阅")
    @PostMapping("terminal/sub")
    public APIResult<DeviceInfo> sub(@RequestParam String clientId) {
        Session session = sessionManager.get(clientId);
        if (session != null) {
            WebLogAdapter.addClient(session.getClientId());
            return new APIResult(session.getAttribute(SessionKey.DeviceInfo));
        }
        return APIResult.SUCCESS;
    }

    @Operation(summary = "websocket取消订阅")
    @PostMapping("terminal/unsub")
    public APIResult unsub(@RequestParam String clientId) {
        WebLogAdapter.removeClient(clientId);
        return APIResult.SUCCESS;
    }

    @Operation(summary = "808协议分析工具")
    @RequestMapping(value = "message/explain", method = {RequestMethod.POST, RequestMethod.GET})
    public String decode(@Parameter(description = "16进制报文") @RequestParam String hex) {
        Explain explain = new Explain();
        hex = hex.replace(" ", "");
        String[] lines = hex.split("\n");
        for (String line : lines) {
            String[] msgs = line.split("7e7e");
            for (String msg : msgs) {
                ByteBuf byteBuf = Unpooled.wrappedBuffer(ByteBufUtil.decodeHexDump(msg));
                decoder.decode(byteBuf, explain);
            }
        }
        List<Info> list = explain.getList();
        StringBuilder result = new StringBuilder(1024);
        for (Info info : list) {
            result.append(info.getIndex() + "\t" + "[" + info.getRaw() + "] " + info.getDesc() + ": " + StrUtils.toString(info.getValue())).append('\n');
        }
        return result.toString();
    }

    @Operation(summary = "原始消息发送")
    @PostMapping("terminal/raw")
    public String postRaw(@Parameter(description = "终端手机号") @RequestParam String clientId,
                          @Parameter(description = "16进制报文") @RequestParam String message) {
        Session session = sessionManager.get(clientId);
        if (session != null) {
            ByteBuf byteBuf = Unpooled.wrappedBuffer(ByteBufUtil.decodeHexDump(message));
            session.notify(byteBuf);
            return "success";
        }
        return "fail";
    }

    @Operation(summary = "位置信息查询")
    @GetMapping("location")
    public Pagination<Location> find(LocationQuery query, PageInfo pageInfo) {
        Pagination<Location> result = Page.start(() -> locationService.find(query), pageInfo);
        return result;
    }

    @Operation(summary = "修改日志级别")
    @GetMapping("logger")
    public String logger(@RequestParam LogUtils.Lv level) {
        LogUtils.setLevel(level.value);
        return "success";
    }

}