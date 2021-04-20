/* DOM加载完毕后新建Vue实例 */
$(document).ready(function(){
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
            getSimilarMovieRecList:function () {
                $.ajax({
                    type:'post',
                    url:'/movie/' + this.movieId + '/getSimilarMovieRecList',
                    data:{
                        size : 30
                    },
                    dataType: "json",
                    success: function (similarMovieList){
                        $.each(similarMovieList, function (index, similarMovie){
                            vm.similarMovieList.push(similarMovie);
                            //console.log(similarMovie);
                        })
                    }
                })
            },
            shuffle: function () {
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
                //原数保留两位小数，乘100后进行上舍入再除以100
                return Math.ceil(value * 100) / 100;
            }
        }
    })
})