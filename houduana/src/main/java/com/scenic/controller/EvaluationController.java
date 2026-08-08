package com.scenic.controller;

import com.scenic.entity.Evaluation;
import com.scenic.service.EvaluationService;
import com.scenic.util.JwtUtil;
import com.scenic.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/evaluation")
public class EvaluationController {

    @Autowired
    private EvaluationService evaluationService;

    @Autowired
    private JwtUtil jwtUtil;

    /** 评价列表（管理端，分页 + 筛选） */
    @GetMapping("/list")
    public Result list(@RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "10") int size,
                       @RequestParam(required = false) String orderNo,
                       @RequestParam(required = false) String spotName,
                       @RequestParam(required = false) Integer rating,
                       @RequestParam(required = false) String startDate,
                       @RequestParam(required = false) String endDate) {
        Map<String, Object> data = evaluationService.listAll(page, size, orderNo, spotName, rating, startDate, endDate);
        return Result.success(data);
    }

    /** 查找某订单的评价（游客端订单详情页） */
    @GetMapping("/order")
    public Result getByOrder(@RequestParam Long orderId) {
        return Result.success(evaluationService.getByOrderId(orderId));
    }

    /** 提交评价（兼容 score / rating 两种评分字段） */
    @PostMapping("/submit")
    public Result submit(@RequestBody Map<String, Object> params,
                         @RequestHeader("Authorization") String authHeader) {
        try {
            Long userId = currentUserId(authHeader);
            Evaluation evaluation = new Evaluation();
            evaluation.setOrderId(toLong(params.get("orderId")));
            evaluation.setScore(resolveScore(params));
            evaluation.setContent(params.get("content") == null ? null : params.get("content").toString());
            Evaluation saved = evaluationService.submit(evaluation, userId);
            return Result.success(saved);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(e.getMessage());
        }
    }

    /** 更新评价（支持传 id 或 orderId） */
    @PutMapping("/update")
    public Result update(@RequestBody Map<String, Object> params) {
        try {
            Evaluation evaluation = new Evaluation();
            evaluation.setId(toLong(params.get("id")));
            evaluation.setOrderId(toLong(params.get("orderId")));
            evaluation.setScore(resolveScore(params));
            evaluation.setContent(params.get("content") == null ? null : params.get("content").toString());
            Evaluation updated = evaluationService.update(evaluation);
            return Result.success(updated);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(e.getMessage());
        }
    }

    private Long toLong(Object obj) {
        if (obj == null) return null;
        try {
            return Long.valueOf(obj.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private Integer resolveScore(Map<String, Object> params) {
        Object score = params.get("score");
        Object rating = params.get("rating");
        if (score != null) {
            try {
                return Integer.valueOf(score.toString());
            } catch (Exception ignored) {
            }
        }
        if (rating != null) {
            try {
                return Integer.valueOf(rating.toString());
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /** 删除评价（管理端） */
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Long id) {
        try {
            evaluationService.delete(id);
            return Result.success("删除成功");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("删除失败：" + e.getMessage());
        }
    }

    /** 兼容旧路径 */
    @DeleteMapping("/delete/{id}")
    public Result deleteOld(@PathVariable Long id) {
        return delete(id);
    }

    private Long currentUserId(String authHeader) {
        try {
            String token = authHeader.substring(7);
            return Long.parseLong(jwtUtil.getUserIdFromToken(token));
        } catch (Exception e) {
            return null;
        }
    }
}
