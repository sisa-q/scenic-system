package com.scenic.controller;

import com.scenic.entity.Notice;
import com.scenic.service.NoticeService;
import com.scenic.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notice")
public class NoticeController {

    @Autowired
    private NoticeService noticeService;

    @GetMapping("/list")
    public Result list(@RequestParam(required = false) Integer page,
                       @RequestParam(required = false) Integer size) {
        // 传入 page/size 时走数据库分页，否则保持旧的全量列表（兼容游客端）
        if (page != null && size != null) {
            return Result.success(noticeService.pageAll(page, size));
        }
        List<Notice> list = noticeService.listAll();
        return Result.success(list);
    }

    @GetMapping("/detail/{id}")
    public Result detail(@PathVariable Long id) {
        Notice notice = noticeService.getById(id);
        return notice == null ? Result.error("公告不存在") : Result.success(notice);
    }

    @PostMapping("/add")
    public Result add(@RequestBody Notice notice) {
        return Result.success(noticeService.add(notice));
    }

    @PutMapping("/update")
    public Result update(@RequestBody Notice notice) {
        return Result.success(noticeService.update(notice));
    }

    @DeleteMapping("/delete/{id}")
    public Result delete(@PathVariable Long id) {
        noticeService.delete(id);
        return Result.success("删除成功");
    }
}