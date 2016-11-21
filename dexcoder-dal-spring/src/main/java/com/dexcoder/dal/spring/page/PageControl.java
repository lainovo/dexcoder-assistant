package com.dexcoder.dal.spring.page;

import java.sql.DatabaseMetaData;
import java.util.List;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.jdbc.core.JdbcTemplate;

import com.dexcoder.commons.pager.Pageable;
import com.dexcoder.commons.pager.Pager;

/**
 * 分页拦截器
 *
 * Created by liyd on 6/26/14.
 */
@Aspect
public class PageControl {

    /** 分页线程变量 */
    public static final ThreadLocal<Pager>    LOCAL_PAGER     = new ThreadLocal<Pager>();

    /** 获取总记录数 */
    private static final ThreadLocal<Boolean> GET_ITEMS_TOTAL = new ThreadLocal<Boolean>();

    /** 数据库 */
    public static String                      DATABASE;

    /** 分页sql处理器 */
    private PageSqlHandler                    pageSqlHandler;

    /**
     * 执行分页
     *
     * @param pageable
     */
    public static void performPage(Pageable pageable) {
        performPage(pageable.getCurPage(), pageable.getItemsPerPage(), true);
    }

    /**
     * 执行分页
     *
     * @param pageable
     */
    public static void performPage(Pageable pageable, boolean isGetCount) {
        performPage(pageable.getCurPage(), pageable.getItemsPerPage(), isGetCount);
    }

    /**
     * 执行分页
     *
     * @param curPage
     * @param itemsPerPage
     */
    public static void performPage(int curPage, int itemsPerPage) {
        performPage(curPage, itemsPerPage, true);
    }

    /**
     * 执行分页
     *
     * @param curPage
     * @param itemsPerPage
     * @param isGetCount
     */
    public static void performPage(int curPage, int itemsPerPage, boolean isGetCount) {
        Pager pager = new Pager();
        pager.setCurPage(curPage);
        pager.setItemsPerPage(itemsPerPage);
        GET_ITEMS_TOTAL.set(isGetCount);
        LOCAL_PAGER.set(pager);
    }

    /**
     * 获取Pager对象
     * 
     * @return
     */
    public static Pager getPager() {
        Pager pager = LOCAL_PAGER.get();
        //获取数据时清除
        LOCAL_PAGER.remove();
        GET_ITEMS_TOTAL.remove();
        return pager;
    }

    /**
     * 设置pager对象
     */
    public static void setPager(Pager pager) {
        LOCAL_PAGER.set(pager);
    }

    @Pointcut("execution(* org.springframework.jdbc.core.JdbcOperations.query*(..))")
    public void queryMethod() {
        //该方法没实际作用，只是切面声明对象，声明一个切面的表达式
        //下面使用时，只需要写入这个表达式名(方法名)即可   等同于
        //@Before("anyMethod()") == @Before("execution(* org.springframework.jdbc.core.JdbcOperations.query*(..))")
        //可以是private修饰符，但是会有never used的警告，所以这里用了public
    }

    @Around("queryMethod()")
    public Object pagerAspect(ProceedingJoinPoint pjp) throws Throwable {

        if (LOCAL_PAGER.get() == null) {
            return pjp.proceed();
        }

        JdbcTemplate target = (JdbcTemplate) pjp.getTarget();
        if (DATABASE == null) {
            DatabaseMetaData metaData = target.getDataSource().getConnection().getMetaData();
            DATABASE = metaData.getDatabaseProductName().toUpperCase();
        }

        Object[] args = pjp.getArgs();
        String querySql = (String) args[0];
        Pager pager = LOCAL_PAGER.get();
        args[0] = this.getPageSqlHandler().getPageSql(querySql, pager, DATABASE);

        if (GET_ITEMS_TOTAL.get()) {

            String countSql = this.getPageSqlHandler().getCountSql(querySql, pager, DATABASE);
            Object[] countArgs = null;
            for (Object obj : args) {
                if (obj instanceof Object[]) {
                    countArgs = (Object[]) obj;
                }
            }
            int itemsTotal = target.queryForObject(countSql, countArgs, Integer.class);
            pager.setItemsTotal(itemsTotal);
        }
        Object result = pjp.proceed(args);
        pager.setList((List<?>) result);

        return result;
    }

    public PageSqlHandler getPageSqlHandler() {
        if (pageSqlHandler == null) {
            pageSqlHandler = new SimplePageSqlHandler();
        }
        return pageSqlHandler;
    }

    public void setPageSqlHandler(PageSqlHandler pageSqlHandler) {
        this.pageSqlHandler = pageSqlHandler;
    }
}