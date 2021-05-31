// Login Dialog Control
$(document).ready(function(){
    Vue.prototype.$axios = axios;

    var vm = new Vue({
        el: "#login-register-dialog",
        data() {
            var validateUsername = (rule, value, callback) => {
                if (!value || value === '') {
                    return callback(new Error('用户名不能为空'));
                }
                callback();
            };
            var validatePassword = (rule, value, callback) => {
                if (!value || value === '') {
                    return callback(new Error('请输入密码'));
                }
                callback();
            };
            var validateRegUsername = (rule, value, callback) => {
                if (!value || value === '') {
                    return callback(new Error('用户名不能为空'));
                }
                callback();
            };
            var validateRegPassword = (rule, value, callback) => {
                if (!value || value === '') {  //密码为空
                    return callback(new Error('请输入密码'));
                }
                else if (!this.pwdRegex.test(value)) { //不匹配正则表达式
                    return callback(new Error('密码必须由字母数字特殊符号组成,6~20位'));
                }
                else if (this.regRuleForm.checkPassword !== ''){
                    this.$refs.regRuleForm.validateField('checkPassword');
                    return callback();
                }
                callback();
            };
            var validateCheckPassword = (rule, value, callback) => {
                if(!value || value === ''){
                    callback(new Error('请再次输入密码!'));
                }else if(value !== this.regRuleForm.password){
                    callback(new Error('两次输入的密码不一致!'));
                }else{
                    callback();
                }
            };
            return {
                activeTab: 'loginTab',
                pwdRegex: /^(?=.*[a-zA-Z])(?=.*[0-9])(?=.*[._~!@#$^&*])[A-Za-z0-9._~!@#$^&*]{6,20}$/,
                ruleForm: {
                    username: '',
                    password: '',
                    nameErrorMsg: '',
                    pwdErrorMsg: '',
                    loading: false
                },
                regRuleForm: {
                    username: '',
                    password: '',
                    checkPassword: '',
                    nameErrorMsg: '',
                    loading: false
                },
                rules: {
                    username: [
                        { validator: validateUsername, trigger: 'blur'}
                    ],
                    password: [
                        { validator: validatePassword, trigger: 'blur'}
                    ],
                },
                regRules: {
                    username: [
                        { validator: validateRegUsername, trigger: 'blur'}
                    ],
                    password: [
                        { validator: validateRegPassword, trigger: ['change', 'blur']}
                    ],
                    checkPassword: [
                        { validator: validateCheckPassword, trigger: ['change', 'blur']}
                    ]
                }
            }
        },
        methods: {
            makeErrEmpty() {
                this.ruleForm.nameErrorMsg = '';
                this.ruleForm.pwdErrorMsg = '';
            },
            login(formName) {
                //调用字段检查，检查通过才可进行下一步
                this.$refs[formName].validate((valid) => {
                    if (!valid) {
                        console.log('Validate Error');
                        return false;
                    }
                });
                //清空后台回显信息
                this.makeErrEmpty();
                //等待回显的状态控制
                this.ruleForm.loading = true;
                this.$axios({
                    method: 'post',
                    url: '/user/login',
                    data: {
                        username: this.ruleForm.username,
                        password: this.ruleForm.password
                    }
                })
                    .then(r => {
                        if(r.data['status'] === 'success'){
                            setTimeout(() => {
                                window.location.href = "/homepage";//跳转
                            }, 1000);
                        }else{
                            this.$nextTick(() => {
                                if(r.data['error_code'] === '404.1')
                                    this.ruleForm.nameErrorMsg = r.data['error_msg'];
                                else if(r.data['error_code'] === '404.2')
                                    this.ruleForm.pwdErrorMsg = r.data['error_msg'];
                            })
                        }
                        this.ruleForm.loading = false;
                    })
                    .catch(error => {
                        console.log(error);
                    })
            },
            register(formName) {
                this.$refs[formName].validate((valid) => {
                    if(!valid)
                        return false;
                });
                //等待回显
                this.regRuleForm.loading = true;
                this.$axios({
                    method: 'post',
                    url: '/user/register',
                    data: {
                        username: this.regRuleForm.username,
                        password: this.regRuleForm.password
                    }
                })
                    .then(r => {
                        //清空nameErrorMsg，以便回显后台信息
                        this.regRuleForm.nameErrorMsg = '';
                        if(r.data['status'] === 'success'){
                            setTimeout(() => {
                                this.ruleForm.username = this.regRuleForm.username;
                                this.ruleForm.password = this.regRuleForm.password;
                                //切换至登录标签页
                                this.activeTab = "loginTab";
                            }, 1000);
                        }else{
                            this.$nextTick(() => {
                                this.regRuleForm.nameErrorMsg = r.data['error_msg'];
                            })
                        }
                        this.regRuleForm.loading = false;
                    })
                    .catch(error => {
                        console.log(error);
                    })
            },
            resetForm(formName) {
                this.$refs[formName].resetFields();
            }
        }
    })
})