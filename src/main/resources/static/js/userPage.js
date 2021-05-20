$(document).ready(function (){
    var vm = new Vue({
        el: '#vue_load_user_info',
        data: {
            userId: $("#userIdContainer").val(),
            ratingsList: [],
            watchList: [],
            recList: [],
            curPageIndex: 1,
            pageSize: 7
        },
        computed: {
            totalLength: function (){
                return this.recList.length;
            },
            curShowingList: function(){
                if (this.curPageIndex * this.pageSize >= this.totalLength)    //当前页不满足有pageSize个item时，默认使用最后pageSize个Item作为当前页里的各item
                    return this.recList.slice(-this.pageSize)
                else
                    return this.recList.slice((this.curPageIndex - 1) * this.pageSize, this.curPageIndex * this.pageSize);
            },
            isFirstPage: function (){
                return this.curPageIndex === 1;
            },
            isLastPage: function (){
                return this.curPageIndex * this.pageSize >= this.totalLength;
            }
        },
        mounted: function (){
            this.getRatingsList();
            this.getWatchList();
            this.getRecList();
        },
        methods: {
            getRatingsList: function (){
                this.$axios({
                    method: 'post',
                    url: '/user/' + this.userId + "/getRatingsList",
                    params: {
                        size: 6
                    }
                })
                    .then(r => {
                        $.each(r.data, (index, rating) => {
                            this.ratingsList.push(rating);
                        })
                    })
                    .catch(error => { console.log(error) });
            },
            getWatchList: function (){
                this.$axios({
                    method: 'post',
                    url: '/watchlist/getWatchList',
                    params: {
                        userId: this.userId,
                        size: 5
                    }
                })
                    .then(r => {
                        $.each(r.data, (index, movie) => {
                            //每个movie都在watchList里，设定isInWatchList和watchListLoading
                            movie.isInWatchList = true;
                            movie.watchListLoading = false;
                            this.watchList.push(movie);
                        })
                        //console.log(this.watchList);
                    })
                    .catch(error => { console.log(error) });
            },
            getRecList: function(){
                this.$axios({
                    method: 'post',
                    url: '/user/' + this.userId + '/getUserRecList',
                    params: {
                        size: 30
                    }
                })
                    .then(r => {
                        $.each(r.data, (index, movie) => {
                            this.recList.push(movie);
                        })
                        this.getIsInWatchList(this.recList);
                    })
                    .catch(error => { console.log(error) });
            },
            getIsInWatchList: function (movieList){
                this.$axios({
                    method: 'post',
                    url: '/watchlist/getIsInWatchList',
                    data: movieList.map(movie => {return movie.movieId})
                })
                    .then(r => {
                        //返回值是一个Map
                        $.each(r.data, (key, value) => {
                            //注意key(movieId)拿出来时解析为String了，用Number转换后使用过滤器找到其对应的对象
                            //filter返回的是一个Array，由于movieId唯一，因此Array中只有一个对象
                            movie = movieList.filter(movie => {return movie.movieId === Number.parseInt(key)})[0];
                            this.$set(movie, "isInWatchList", value);
                            //设置Bool变量控制加载动画(实际是SVG替换，ElementsUI的loading太丑了)
                            this.$set(movie, "watchListLoading", false);
                        })
                    })
                    .catch(error => { console.log(error) });
            },
            nextPage: function () {
                if (!this.isLastPage)
                    this.curPageIndex++;
            },
            prevPage: function () {
                if (!this.isFirstPage)
                    this.curPageIndex--;
            },
        },
        components: {
            'watchlist-ribbon': watchListRibbon
        },
        filters: {
            ratingFormat: function(value) {
                //评价分数保留两位小数，乘100后进行上舍入再除以100
                return Math.ceil(value * 100) / 100;
            }
        }
    })
})