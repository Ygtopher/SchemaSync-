package com.dbtool.model;

import java.util.ArrayList;
import java.util.List;

public class VisualJoinState {
    public String baseTable;
    public boolean distinct;
    public List<String> selectedColumns = new ArrayList<>();
    public List<JoinState> joins = new ArrayList<>();
    public List<WhereState> wheres = new ArrayList<>();
    public List<OrderByState> orderBys = new ArrayList<>();
    public String limit;
    public String offset;


    public static class JoinConditionState {
        public String operator;
        public String leftTable;
        public String leftCol;
        public String rightCol;
    }

    public static class JoinState {
        public String type;
        public String table;
        public String leftTable;
        public String leftCol;
        public String rightCol;
        public List<JoinConditionState> conditions = new ArrayList<>();
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class WhereState {
        public String logic;
        public String table;
        public String column;
        public String operator;
        public String value;

        // Subquery extensions
        public String inType;
        public String subTable;
        public String subCol;
        public boolean subDistinct;
        public List<SubqueryConditionState> subConditions = new ArrayList<>();
        public String subGroupByCol;
        public String subHaving;
        public String subOrderByCol;
        public String subOrderDir = "ASC";
        public String subLimit;
        public String subOffset;
        public boolean useCustomSubquery;
        public String customSubqueryText;
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class SubqueryConditionState {
        public String logic = "AND";
        public String column;
        public String operator = "=";
        public String value;
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderByState {
        public String table;
        public String column;
        public String direction;
    }
}
