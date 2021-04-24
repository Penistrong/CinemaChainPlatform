/* DOM加载完毕后新建Vue实例 */
$(document).ready(function(){
    //将axios.js中的axios挂载到Vue的原生对象中
    Vue.prototype.$axios = axios;

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
                    })
                    .catch(error => {
                        console.log(error);
                    });
                /*
                $.ajax({
                    type:'post',
                    url:'/movie/' + this.movieId + '/getSimilarMovieRecList',
                    data:{
                        size: 30
                    },
                    dataType: 'json',
                    success: function (similarMovieList) {
                        $.each(similarMovieList, function (index, similarMovie){
                            vm.similarMovieList.push(similarMovie);
                            //console.log(similarMovie);
                        });
                    }
                })
                */
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
            }
        },
        filters: {
            ratingFormat: function(value) {
                //评价分数保留两位小数，乘100后进行上舍入再除以100
                return Math.ceil(value * 100) / 100;
            }
        }
    })
})