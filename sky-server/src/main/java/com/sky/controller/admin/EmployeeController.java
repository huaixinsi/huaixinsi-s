package com.sky.controller.admin;

import com.sky.constant.JwtClaimsConstant;
import com.sky.dto.EmployeeDTO;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.entity.Employee;
import com.sky.properties.JwtProperties;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.EmployeeService;
import com.sky.utils.JwtUtil;
import com.sky.vo.EmployeeLoginVO;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 员工管理
 */
@RestController
@Slf4j
public class EmployeeController {

    @Autowired
    private EmployeeService employeeService;
    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 登录
     *
     * @param employeeLoginDTO
     * @return
     */
    @ApiOperation("员工登录")
    @PostMapping("/admin/employee/login")
    public Result<EmployeeLoginVO> login(@RequestBody EmployeeLoginDTO employeeLoginDTO) {
        log.info("员工登录：{}", employeeLoginDTO);

        Employee employee = employeeService.login(employeeLoginDTO);

        //登录成功后，生成jwt令牌
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, employee.getId());
        String token = JwtUtil.createJWT(
                jwtProperties.getAdminSecretKey(),
                jwtProperties.getAdminTtl(),
                claims);

        EmployeeLoginVO employeeLoginVO = EmployeeLoginVO.builder()
                .id(employee.getId())
                .userName(employee.getUsername())
                .name(employee.getName())
                .token(token)
                .build();

        return Result.success(employeeLoginVO);
    }

    /**
     * 退出
     *
     * @return
     */
    @ApiOperation("员工退出")
    @PostMapping("/admin/employee/logout")
    public Result<String> logout() {
        return Result.success();
    }
    @ApiOperation("新增员工")
    @PostMapping("/admin/employee")
    public Result save(@RequestBody EmployeeDTO employeeDTO){
        log.info("新增员工，员工数据：{}",employeeDTO);
         employeeService.save(employeeDTO);
         return Result.success();
    }
    @ApiOperation("员工信息分页查询")
    //员工信息分页查询
    @GetMapping("/admin/employee/page")
    public Result page(EmployeePageQueryDTO employeePageQueryDTO){
        log.info("员工信息分页查询{}", employeePageQueryDTO);
        PageResult<Employee> pageResult = employeeService.page(employeePageQueryDTO);
        log.info("分页结果{}", pageResult);
        return Result.success(pageResult);
    }
    //启用禁用员工账号
    @ApiOperation("启用禁用员工账号")
    @PostMapping("/admin/employee/status/{status}")
    public Result startOrStop(@PathVariable Integer status, Long id){
        log.info("启用禁用员工账号：{}", id);
        employeeService.startOrStop(status, id);
        return Result.success();
    }
    //编辑员工信息
    @ApiOperation("编辑员工信息")
    @PutMapping("/admin/employee")
    public Result update(@RequestBody EmployeeDTO employeeDTO){
        log.info("编辑员工信息：{}", employeeDTO);
        employeeService.update(employeeDTO);
        return Result.success();
    }





}
