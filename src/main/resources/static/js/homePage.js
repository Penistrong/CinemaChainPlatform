$(document).ready(function (){
    var vm = new Vue({
        el: "#vue_load_default_movie_list",
        data: {
            //受不了了,原生Map对象的遍历好丑,不想看,直接用对象代替算了
            //genresMovieList: new Map,
            genresMovieList: {},
        },
        components: {
            'watchlist-ribbon': watchListRibbon
        },
        mounted: function(){
            this.getGenresMovieList();
        },
        methods: {
            getGenresMovieList: function (){
                this.$axios({
                    method: 'post',
                    url: '/homepage/getGenresMovieList',
                    data: {
                        //若要携带，则可选pageIndex,pageSize,listSize
                    }
                })
                    .then(r=> {
                        $.each(r.data, (genre, movieList) => {
                            vm.$set(vm.genresMovieList, genre, movieList);
                            //vm.genresMovieList.set(genre, movieList);
                            this.getIsInWatchList(vm.genresMovieList[genre]);
                        });
                        //Vue 2.x支持ES6的Iterable迭代，即原生的Map和Set都可以使用，但是Vue2.x并不支持可响应的Map和Set，无法自动探测变更
                        //若要使用需要强制更新
                        //vm.$forceUpdate();
                        //console.log(vm.genresMovieList);
                        //另外还需小心模板中的迭代问题
                        //注意使用 for ... of ... 遍历原生Map对象时，每个迭代值都是一个Array，Array[0]存放key，Array[1]存放value
                        /*for(const arr of vm.genresMovieList){
                            console.log(arr);
                        }*/
                    })
                    .catch(error => {
                        console.log(error);
                    })
            },
            getIsInWatchList: function(movieList){
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
    });
});
