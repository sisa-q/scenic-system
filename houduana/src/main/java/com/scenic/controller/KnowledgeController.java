package com.scenic.controller;

import com.scenic.entity.KnowledgeDoc;
import com.scenic.repository.KnowledgeDocRepository;
import com.scenic.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 知识库文档管理（管理员维护，AI 自动读取做 RAG） */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    @Autowired(required = false)
    private KnowledgeDocRepository docRepository;

    @GetMapping("/list")
    public Result list() {
        if (docRepository == null) return Result.success(List.of());
        return Result.success(docRepository.findAll());
    }

    @PostMapping("/add")
    public Result add(@RequestBody KnowledgeDoc doc) {
        try {
            if (doc.getTitle() == null || doc.getTitle().trim().isEmpty()) {
                return Result.error("标题不能为空");
            }
            if (doc.getContent() == null || doc.getContent().trim().isEmpty()) {
                return Result.error("内容不能为空");
            }
            doc.setId(null);
            return Result.success(docRepository.save(doc));
        } catch (Exception e) {
            return Result.error("新增失败：" + e.getMessage());
        }
    }

    @PutMapping("/update")
    public Result update(@RequestBody KnowledgeDoc doc) {
        try {
            if (doc.getId() == null || docRepository == null || !docRepository.existsById(doc.getId())) {
                return Result.error("文档不存在");
            }
            return Result.success(docRepository.save(doc));
        } catch (Exception e) {
            return Result.error("更新失败：" + e.getMessage());
        }
    }

    @DeleteMapping("/delete/{id}")
    public Result delete(@PathVariable Long id) {
        try {
            if (docRepository == null) return Result.error("服务未启用");
            docRepository.deleteById(id);
            return Result.success("删除成功");
        } catch (Exception e) {
            return Result.error("删除失败：" + e.getMessage());
        }
    }
}
