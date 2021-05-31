/* DOM加载完毕后新建Vue实例 */
$(document).ready(function(){
    var vm_moviedetail = new Vue({
        el: "#movieDetailsContainer",
        data: {
            movieId: $("#movieIdContainer").val(),
            movie: {},
            ratingValue: null,
            isRatingRequestHandling: false,
            ratingTips: "Give your rating now!",
            colors: ['#99A9BF', '#F5C518', '#FF9900'],
            //注意这个评分icon不是ElementsUI自带的,下载地址https://github.com/ElemeFE/element/tree/dev/examples/assets/styles/fonts
            iconClasses: ['icon-rate-face-1', 'icon-rate-face-2', 'icon-rate-face-3'],
        },
        computed: {
            averageRating: function (){
                return Math.ceil(this.movie.averageRating * 100) / 100;
            }
        },
        created: function (){
            //使用Loadsh的_.debounce限制评分更新的执行频率
            //设置1000ms的间隔时间
            this.debouncedRateMovie = _.debounce(this.rateMovie, 1000);
            //获取评分历史记录
            //注意，由于要监听ratingValue值的变化，如果直接在watch里监听，这里初始化评分的时候会一并触发导致又更新了一次评分
            //所以在回调里使用命令式watch
            this.$axios({
                method: 'post',
                url: '/movie/' + this.movieId +'/getUserRatingValue'
            })
                .then(r => {
                    if(r.data["status"] === 'success') {
                        this.ratingTips = "Your Rating"
                        this.ratingValue = Number.parseFloat(r.data['ratingValue']);
                    }
                    //初始化完毕再开始监听
                    this.$watch('ratingValue', function (newRatingValue, oldRatingValue){
                        this.debouncedRateMovie(newRatingValue, oldRatingValue);
                    });
                })
                .catch(error => { console.log(error) });
        },
        mounted: function (){
            this.getMovieDetail(this.movieId);
        },
        methods: {
            getMovieDetail: function (movieId){
                this.$axios({
                    method: 'post',
                    url: '/movie/' + movieId,
                })
                    .then(r => {
                        this.movie = r.data;
                    })
                    .catch(error => {
                        console.log(error);
                    })
            },
            rateMovie: function (newRatingValue, oldRatingValue){
                this.isRatingRequestHandling = true;
                this.$axios({
                    method: 'post',
                    url: '/movie/' + this.movieId + "/rateMovie",
                    params: {
                        'ratingValue': newRatingValue
                    }
                })
                    .then(r => {
                        if(r.data['status'] === 'success'){
                            this.ratingTips = "Thanks for your rating!"
                        }else {
                            console.log(r.data['error_msg']);
                            //评分失败,回滚原值
                            //直接回滚导致不断重复侦听器里的方法
                            //this.ratingValue = oldRatingValue;
                        }
                        this.isRatingRequestHandling = false;
                    })
                    .catch(error => { console.log(error) });
            }
        },
        filters: {
            ratingFormat: function(value) {
                //评价分数保留两位小数，乘100后进行上舍入再除以100
                return Math.ceil(value * 100) / 100;
            },
            ratingNumberFormat: function(value){
                if(value < 1000)
                    return value;
                else
                    return (value / 1000).toFixed(1) + "K";
            }
        },
    })

    var vm = new Vue({
        el: "#vue_load_similar_movie",
        data: {
            movieId : $("#movieIdContainer").val(),
            similarMovieList: [],
            curPageIndex: 1,
            pageSize: 7
        },
        computed: {
            totalLength: function (){
                return this.similarMovieList.length;
            },
            curShowingList: function(){
                if (this.curPageIndex * this.pageSize >= this.totalLength)    //当前页不满足有pageSize个item时，默认使用最后pageSize个Item作为当前页里的各item
                    return this.similarMovieList.slice(-this.pageSize)
                else
                    return this.similarMovieList.slice((this.curPageIndex - 1) * this.pageSize, this.curPageIndex * this.pageSize);
            },
            isFirstPage: function (){
                return this.curPageIndex === 1;
            },
            isLastPage: function (){
                return this.curPageIndex * this.pageSize >= this.totalLength;
            }
        },
        mounted: function(){
            //获取相似电影推荐列表
            this.getSimilarMovieRecList();
        },
        methods: {
            getSimilarMovieRecList: function () {
                this.$axios({
                    method: 'post',
                    url: '/movie/' + this.movieId + '/getSimilarMovieRecList',
                    data: {
                        size: 30
                    }
                })
                    .then(r => {
                        //注意与$.ajax不同的情况，回调中参数是一个包含axios.config在内的各种参数的对象，使用r.data取到真正的回调json数据
                        $.each(r.data, function (index, similarMovie){
                            vm.similarMovieList.push(similarMovie);
                        })
                        //获取推荐列表中各电影是否在当前登录用户的待看列表里
                        //注意只能写在获得了推荐列表后的回调里，否则因为axios的异步，若直接在mounted里顺序执行函数
                        //会导致发送请求时，电影列表为空
                        this.getIsInWatchList(this.similarMovieList);
                    })
                    .catch(error => {
                        console.log(error);
                    });
            },
            shuffle: function () {
                //_.shuffle lodash或者underscore自带的函数
                this.similarMovieList = _.shuffle(this.similarMovieList);
            },
            nextPage: function () {
                if (!this.isLastPage)
                    this.curPageIndex++;
            },
            prevPage: function () {
                if (!this.isFirstPage)
                    this.curPageIndex--;
            },
            /*
                由于movie是由v-for渲染出来的，要给循环中出现的每个movie添加一个isInWatchList属性值，并据此控制添加与移出的切换逻辑
                axios执行PUSH的回调函数中调用该函数，向后台请求similarMovieList中各movie是否在当前用户的待看列表中
                如果要给已经创建的实例中的某对象添加新的根级响应元素，如果该元素事先未定义那么其不会响应，视图不会更新
                这个时候使用vm.$set(object, key, value)绑定新的响应属性
            */
            getIsInWatchList: function(movieList) {
                this.$axios({
                    method: 'post',
                    url: '/watchlist/getIsInWatchList',
                    data: movieList.map(movie => {return movie.movieId})
                })
                    .then(r => {
                        //返回值是一个Map
                        $.each(r.data, function(key, value){
                            //注意key(movieId)拿出来时解析为String了，用Number转换后使用过滤器找到其对应的对象
                            //filter返回的是一个Array，由于movieId唯一，因此Array中只有一个对象
                            movie = movieList.filter(movie => {return movie.movieId === Number.parseInt(key)})[0];
                            vm.$set(movie, "isInWatchList", value);
                            //设置Bool变量控制加载动画(实际是SVG替换，ElementsUI的loading太丑了)
                            vm.$set(movie, "watchListLoading", false);
                        })
                    })
                    .catch(error => {
                        console.log(error);
                    })
            }
        },
        filters: {
            ratingFormat: function(value) {
                //评价分数保留两位小数，乘100后进行上舍入再除以100
                return Math.ceil(value * 100) / 100;
            }
        },
        components: {
            'watchlist-ribbon' : watchListRibbon
        }
    })
})