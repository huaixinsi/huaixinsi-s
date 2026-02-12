package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.PasswordConstant;
import com.sky.constant.StatusConstant;
import com.sky.context.BaseContext;
import com.sky.dto.EmployeeDTO;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.dto.PasswordEditDTO;
import com.sky.entity.Employee;
import com.sky.exception.AccountLockedException;
import com.sky.exception.AccountNotFoundException;
import com.sky.exception.PasswordEditFailedException;
import com.sky.exception.PasswordErrorException;
import com.sky.mapper.EmployeeMapper;
import com.sky.result.PageResult;
import com.sky.service.EmployeeService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    @Autowired
    private EmployeeMapper employeeMapper;

    /**
     * 员工登录
     *
     * @param employeeLoginDTO
     * @return
     */
    public Employee login(EmployeeLoginDTO employeeLoginDTO) {
        String username = employeeLoginDTO.getUsername();
        String password = employeeLoginDTO.getPassword();

        //1、根据用户名查询数据库中的数据
        Employee employee = employeeMapper.getByUsername(username);

        //2、处理各种异常情况（用户名不存在、密码不对、账号被锁定）
        if (employee == null) {
            //账号不存在
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        //密码比对
        // TODO 后期需要进行md5加密，然后再进行比对
        String passwordMD5 = DigestUtils.md5DigestAsHex(password.getBytes());
        if (!passwordMD5.equals(employee.getPassword())) {
            //密码错误
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }

        if (employee.getStatus() == StatusConstant.DISABLE) {
            //账号被锁定
            throw new AccountLockedException(MessageConstant.ACCOUNT_LOCKED);
        }

        //3、返回实体对象
        return employee;
    }
    //新增员工
    @Transactional
    @Override
    public void save(EmployeeDTO employeeDTO) {
        Employee employee = new Employee();
        BeanUtils.copyProperties(employeeDTO, employee);//拷贝属性
        //密码md5加密处理
        employee.setPassword(DigestUtils.md5DigestAsHex(PasswordConstant.DEFAULT_PASSWORD.getBytes()));
        employee.setStatus(StatusConstant.ENABLE);

        employee.setCreateTime(LocalDateTime.now());
        employee.setUpdateTime(LocalDateTime.now());
        //获取当前登录id
        employee.setCreateUser(BaseContext.getCurrentId());
        employee.setUpdateUser(BaseContext.getCurrentId());
        employeeMapper.save(employee);
    }
    //员工信息分页查询
    @Override
    public PageResult page(EmployeePageQueryDTO employeePageQueryDTO) {
        //设置分页参数
        PageHelper.startPage(employeePageQueryDTO.getPage(), employeePageQueryDTO.getPageSize());
        //开始分页
        List<Employee> employeeList = employeeMapper.page(employeePageQueryDTO);
        //获取分页结果
        Page<Employee> page = (Page<Employee>) employeeList;
        return new PageResult(page.getTotal(), page.getResult());
    }
    //启用禁用员工账号
    @Override
    @Transactional

    public void startOrStop(Integer status, Long id) {
        Employee employee = Employee.builder()
                .status(status)
                .id(id)
                .updateTime(LocalDateTime.now())
                .updateUser(BaseContext.getCurrentId())
                .build();
        employeeMapper.update(employee);
    }
    //根据id查询员工
    @Override
    public Employee getById(Long id) {
        Employee employee = employeeMapper.getById(id);
        return employee;
    }
    //编辑员工信息
    @Override
    public void update(EmployeeDTO employeeDTO) {
        Employee employee = new Employee();
        BeanUtils.copyProperties(employeeDTO, employee);
        employee.setUpdateTime(LocalDateTime.now());
        employee.setUpdateUser(BaseContext.getCurrentId());
        employeeMapper.update(employee);
    }
    //修改密码
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void editPassword(PasswordEditDTO passwordEditDTO) {
        // 1. 核心非空校验
        if (passwordEditDTO == null) {
            throw new PasswordEditFailedException("参数不能为空");
        }
        Long empId = BaseContext.getCurrentId();
        String oldPassword = passwordEditDTO.getOldPassword();
        String newPassword = passwordEditDTO.getNewPassword();

        if (!StringUtils.hasText(oldPassword)) {
            throw new PasswordEditFailedException("旧密码不能为空");
        }
        if (!StringUtils.hasText(newPassword)) {
            throw new PasswordEditFailedException("新密码不能为空");
        }
        // 2. 校验新密码是否与旧密码相同
        if (oldPassword.equals(newPassword)) {
            throw new PasswordEditFailedException("新密码不能与旧密码相同");
        }

        // 3. 查询数据库中的员工信息（验证员工是否存在 + 获取旧密码）
        Employee dbEmployee = employeeMapper.getById(empId);
        if (dbEmployee == null) {
            throw new PasswordEditFailedException("员工不存在，无法修改密码");
        }

        // 4. 加密旧密码（指定UTF-8编码，保证一致性）
        String encryptedOldPwd = DigestUtils.md5DigestAsHex(oldPassword.getBytes(StandardCharsets.UTF_8));
        // 5. 验证旧密码是否正确
        if (!encryptedOldPwd.equals(dbEmployee.getPassword())) {
            throw new PasswordEditFailedException("旧密码输入错误");
        }

        // 6. 加密新密码（指定UTF-8编码）
        String encryptedNewPwd = DigestUtils.md5DigestAsHex(newPassword.getBytes(StandardCharsets.UTF_8));

        // 7. 构建更新对象（仅更新密码、更新时间、更新人）
        Employee employee = Employee.builder()
                .id(empId)
                .password(encryptedNewPwd)
                .updateTime(LocalDateTime.now())
                .updateUser(BaseContext.getCurrentId())
                .build();

        employeeMapper.update(employee);
    }





}
