package net.minecraftforge.common.plon;

import com.google.common.base.Function;
import com.google.common.collect.*;

import java.util.Set;

import static net.minecraftforge.common.plon.AST.*;

/**
 * Created by rainwarrior on 5/18/16.
 */
public abstract class Interpreter<State, Result>
{
    // common static stuff

    private static final ISExp labelSymbol = makeSymbol("&labeled");
    private static final ISExp functionSymbol = makeSymbol("&function");
    private static final ISExp macroSymbol = makeSymbol("&macro");
    static final ISExp mapSymbol = makeSymbol("&map");

    // common stuff

    // TODO in dis
    protected final Function<String, ISExp> reader;
    private final Evaluator macroEvaluator;
    protected final IList topEnv;

    protected Evaluator getMacroEvaluator()
    {
        return macroEvaluator;
    }

    private Interpreter(Function<String, ISExp> reader, IList topEnv, Evaluator macroEvaluator)
    {
        this.reader = reader;
        this.topEnv = topEnv;
        this.macroEvaluator = macroEvaluator;
    }

    private Interpreter(Function<String, ISExp> reader, IList topEnv)
    {
        this.reader = reader;
        this.topEnv = topEnv;
        this.macroEvaluator = new Evaluator(reader, topEnv, null)
        {
            @Override
            protected Evaluator getMacroEvaluator()
            {
                return this;
            }
        };
    }

    public Interpreter(Function<String, ISExp> reader)
    {
        this(reader, lift(makeReadFrame(reader), new Map(PrimOp.values)));
    }

    public Interpreter(Function<String, ISExp> reader, ImmutableMap<? extends ISExp, ? extends ISExp> topEnv)
    {
        this(reader, lift(new Map(topEnv), makeReadFrame(reader), new Map(PrimOp.values)));
    }

    private static Map makeReadFrame(final Function<String, ISExp> reader)
    {
        return new Map(ImmutableMap.of(makeSymbol("load"), new Load()
        {
            @Override
            protected ISExp read(String location)
            {
                return reader.apply(location);
            }
        }));
    }

    // eval stuff

    private static IList bind(IList argNames, IList args, IList env)
    {
        if (length(argNames) != length(args))
        {
            throw new IllegalArgumentException("called bind with lists of different length");
        }
        ImmutableMap.Builder<ISExp, ISExp> builder = ImmutableMap.builder();
        while (argNames != Nil.INSTANCE)
        {
            builder.put(((Cons) argNames).car, ((Cons) args).car);
            argNames = ((Cons) argNames).cdr;
            args = ((Cons) args).cdr;
        }
        ImmutableMap<ISExp, ISExp> frame = builder.build();
        IMap map;
        if (frame.isEmpty())
        {
            map = MNil.INSTANCE;
        }
        else
        {
            map = new Map(frame);
        }
        return new Cons(map, env);

    }

    static void addLabel(ImmutableMap.Builder<ISExp, ISExp> frame, ISExp key, ISExp value)
    {
        if(key instanceof Symbol)
        {
            Symbol name = (Symbol) key;
            frame.put(name, new Cons(labelSymbol, new Cons(value, Nil.INSTANCE)));
            return;
        }
        else if(key instanceof Cons && ((Cons) key).car instanceof Symbol)
        {
            Cons cons = (Cons) key;
            Symbol name = (Symbol) cons.car;
            IList args = cons.cdr;
            frame.put(name, new Cons(labelSymbol, new Cons(args, new Cons(value, Nil.INSTANCE))));
            return;
        }
        throw new IllegalArgumentException("can't label: " + key);
    }

    private static ISExp makeFunction(IList args, ISExp body, IList env)
    {
        return new Cons(functionSymbol, new Cons(args, new Cons(body, new Cons(env, Nil.INSTANCE))));
    }

    protected ISExp lookup(IList env, Symbol name)
    {
        if (env == Nil.INSTANCE)
        {
            return Unbound.INSTANCE;
        }
        Cons cons = (Cons) env;
        if (cons.car == MNil.INSTANCE)
        {
            return lookup(cons.cdr, name);
        }
        if (cons.car instanceof Map)
        {
            Map map = (Map) cons.car;
            if (map.value.containsKey(name))
            {
                return map.value.get(name);
            }
            return lookup(cons.cdr, name);
        }
        throw new IllegalArgumentException("lookup called with a list that has something other than a map:" + cons.car);
    }

    static Map loadFrame(Map map)
    {
        ImmutableMap.Builder<ISExp, ISExp> frameBuilder = ImmutableMap.builder();
        for (java.util.Map.Entry<? extends ISExp, ? extends ISExp> entry : map.value.entrySet())
        {
            addLabel(frameBuilder, entry.getKey(), entry.getValue());
        }
        return new Map(frameBuilder.build());
    }

    protected Result beval(Cons args, IList env, State state)
    {
        if(args.cdr == Nil.INSTANCE)
        {
            return eval(args.car, env, state);
        }
        ISExp definitions = args.car;

        IMap defs = null;

        if (definitions instanceof Map)
        {
            defs = (Map) definitions;
        }
        else
        {
            if(definitions instanceof IList && length(definitions) == 2)
            {
                Cons c1 = (Cons) definitions;
                Cons c2 = (Cons) c1.cdr;
                if (c1.car.equals(makeSymbol("load")) && c2.car instanceof StringAtom)
                {
                    String location = ((StringAtom) c2.car).value;
                    ISExp frameExp = reader.apply(location);
                    if (!(frameExp instanceof IMap))
                    {
                        throw new IllegalArgumentException("load called from bind should return map, got: " + frameExp);
                    }
                    defs = (IMap) frameExp;
                }
            }
        }
        if(defs == MNil.INSTANCE)
        {
            return beval((Cons) args.cdr, env, state);
        }
        if(defs != null)
        {
            return beval((Cons) args.cdr, frameToContext(loadFrame((Map) defs), env, state), state);
        }
        throw new IllegalArgumentException("bind enviroments have to be either maps or load expressions.");
    }

    protected IList frameToContext(Map frame, IList context, State state)
    {
        return new Cons(frame, context);
    }

    protected abstract Result visitSymbol(Symbol symbol, IList env, State state);
    protected abstract Result visitArithmSymbol(ArithmSymbol symbol, IList env, State state);
    protected abstract Result visitAtom(IAtom atom, IList env, State state);
    protected abstract Result visitQuote(ISExp arg, IList env, State state);
    protected abstract Result visitLambda(IList args, ISExp body, IList env, State state);
    protected abstract Result visitMacro(ISExp type, IList args, ISExp body, IList env, State state);
    protected abstract Result visitLabel(ISExp label);
    protected abstract Result visitFold(Result func, Result res, Result list, IList env, State state);
    //protected abstract Result visitMFold(Result func, Result res, Result map, IList env, State state);
    protected abstract Result visitHas(Result label, Result sum, Result f1, Result f2, IList env, State state);
    protected abstract Result visitApply(Result func, ImmutableList<Result> args, IList env, State state);
    protected abstract Result visitMap(Map map, IList env, State state);

    @SuppressWarnings("StringEquality")
    protected Result eval(ISExp exp, IList env, State state)
    {
        if (exp instanceof Symbol)
        {
            return visitSymbol((Symbol) exp, env, state);
        }
        else if (exp instanceof ArithmSymbol)
        {
            return visitArithmSymbol((ArithmSymbol) exp, env, state);
        }
        else if (exp instanceof IAtom)
        {
            return visitAtom((IAtom) exp, env, state);
        }
        else if (exp instanceof Cons)
        {
            Cons cons = (Cons) exp;
            if (cons.car instanceof Symbol)
            {
                String name = ((Symbol) cons.car).value;
                if (name == "quote")
                {
                    if (length(cons.cdr) != 1)
                    {
                        throw new IllegalArgumentException("quote needs 1 argument, got: " + cons.cdr);
                    }
                    Cons cdr = (Cons) cons.cdr;
                    return visitQuote(cdr.car, env, state);
                }
                else if (name == "lambda")
                {
                    if (length(cons.cdr) != 2)
                    {
                        throw new IllegalArgumentException("lambda needs 2 arguments, got: " + cons.cdr);
                    }
                    Cons c2 = (Cons) cons.cdr;
                    Cons c3 = (Cons) c2.cdr;
                    if (!(c2.car instanceof IList))
                    {
                        throw new IllegalArgumentException("lambda needs a list as a first argument, got: " + c2.car);
                    }
                    IList args = (IList) c2.car;
                    ISExp body = c3.car;
                    return visitLambda(args, body, env, state);
                }
                else if (name == "bind")
                {
                    if (cons.cdr == Nil.INSTANCE)
                    {
                        throw new IllegalArgumentException("bind needs at least 1 argument");
                    }
                    Cons args = (Cons) cons.cdr;
                    return beval(args, env, state);
                }
                else if (name == "macro")
                {
                    if (length(cons.cdr) != 3)
                    {
                        throw new IllegalArgumentException("macro needs 3 arguments, got: " + cons.cdr);
                    }
                    Cons c2 = (Cons) cons.cdr;
                    Cons c3 = (Cons) c2.cdr;
                    Cons c4 = (Cons) c3.cdr;
                    if (!(c2.car instanceof IList))
                    {
                        throw new IllegalArgumentException("macro needs a list as a second argument, got: " + c2.car);
                    }
                    return visitMacro(c2.car, (IList) c3.car, c4.car, env, state);
                }
                else if(name == "label")
                {
                    if (length(cons.cdr) != 1 || !((((Cons) cons.cdr).car) instanceof StringAtom))
                    {
                        throw new IllegalArgumentException("label needs 1 string argument, got: " + cons.cdr);
                    }
                    return visitLabel(((Cons) cons.cdr).car);
                }
                else if(name == "fold")
                {
                    if (length(cons.cdr) != 3)
                    {
                        throw new IllegalArgumentException("fold needs 3 arguments, got: " + cons.cdr);
                    }
                    Cons c1 = (Cons) cons.cdr;
                    Cons c2 = (Cons) c1.cdr;
                    Cons c3 = (Cons) c2.cdr;

                    Result f = eval(c1.car, env, state);
                    Result ret = eval(c2.car, env, state);
                    Result list = eval(c3.car, env,state);

                    return visitFold(f, ret, list, env, state);
                }
                else if(name == "has")
                {
                    if (length(cons.cdr) != 4)
                    {
                        throw new IllegalArgumentException("has needs 4 arguments, got: " + cons.cdr);
                    }
                    Cons c1 = (Cons) cons.cdr;
                    Cons c2 = (Cons) c1.cdr;
                    Cons c3 = (Cons) c2.cdr;
                    Cons c4 = (Cons) c3.cdr;

                    Result label = eval(c1.car, env, state);
                    Result sum = eval(c2.car, env, state);
                    Result f1 = eval(c3.car, env,state);
                    Result f2 = eval(c4.car, env,state);

                    return visitHas(label, sum, f1, f2, env, state);
                }
                /*else if(name == "mfold")
                {
                    if (length(cons.cdr) != 3)
                    {
                        throw new IllegalArgumentException("mfold needs 3 arguments, got: " + cons.cdr);
                    }
                    Cons c1 = (Cons) cons.cdr;
                    Cons c2 = (Cons) c1.cdr;
                    Cons c3 = (Cons) c2.cdr;

                    Result f = eval(c1.car, env, state);
                    Result ret = eval(c2.car, env, state);
                    Result map = eval(c3.car, env, state);
                    return visitMFold(f, ret, map, env, state);
                }*/
                Symbol symbol = (Symbol) cons.car;
                if(getMacroEvaluator().isMacro(symbol))
                {
                    return eval(getMacroEvaluator().expandMacro(symbol, cons.cdr), env, state);
                }
            }
            Result func = eval(cons.car, env, state);
            IList args = cons.cdr;
            ImmutableList.Builder<Result> argsBuilder = ImmutableList.builder();
            while(args != Nil.INSTANCE)
            {
                Cons c2 = (Cons) args;
                argsBuilder.add(eval(c2.car, env, state));
                args = c2.cdr;
            }
            return visitApply(func, argsBuilder.build(), env, state);
        }
        else if(exp instanceof Map)
        {
            return visitMap((Map) exp, env, state);
        }
        throw new IllegalStateException("eval of " + exp);
    }

    public static class Evaluator extends Interpreter<Void, ISExp>
    {
        private Evaluator(Function<String, ISExp> reader, IList topEnv, Evaluator macroEvaluator)
        {
            super(reader, topEnv, macroEvaluator);
        }

        public Evaluator(Function<String, ISExp> reader)
        {
            super(reader);
        }

        public Evaluator(Function<String, ISExp> reader, ImmutableMap<? extends ISExp, ? extends ISExp> topEnv)
        {
            super(reader, topEnv);
        }

        public ISExp eval(ISExp exp)
        {
            return eval(exp, topEnv, null);
        }

        @Override
        public ISExp visitSymbol(Symbol symbol, IList env, Void state)
        {
            ISExp value = unlabel(lookup(env, symbol), env);
            if(value == Unbound.INSTANCE)
            {
                throw new IllegalStateException("Undefined variable " + symbol);
            }
            return value;
        }

        @Override
        public ISExp visitArithmSymbol(ArithmSymbol symbol, IList env, Void state)
        {
            return new ArithmOp(symbol);
        }

        @Override
        public ISExp visitAtom(IAtom atom, IList env, Void state)
        {
            return atom;
        }

        @Override
        public ISExp visitQuote(ISExp arg, IList env, Void state)
        {
            return arg;
        }

        @Override
        public ISExp visitLambda(IList args, ISExp body, IList env, Void state)
        {
            return makeFunction(args, body, env);
        }

        @Override
        public ISExp visitMacro(ISExp type, IList args, ISExp body, IList env, Void state)
        {
            type = new Unifier().reify(type, Maps.<ISExp, ISExp>newHashMap(), ImmutableSet.<ISExp>of());
            return new Cons(macroSymbol, new Cons(type, new Cons(args, new Cons(body, new Cons(env, Nil.INSTANCE)))));
        }

        @Override
        protected ISExp visitLabel(ISExp label)
        {
            return lift(makeString("label"), label);
        }

        @Override
        public ISExp visitFold(ISExp func, ISExp res, ISExp list, IList env, Void state)
        {
            while(list != Nil.INSTANCE)
            {
                Cons c4 = (Cons) list;
                res = visitApply(func, ImmutableList.of(res, c4.car), env, state);
                list = c4.cdr;
            }
            return res;
        }

        @Override
        protected ISExp visitHas(ISExp label, ISExp sum, ISExp f1, ISExp f2, IList env, Void state)
        {
            Cons sc = (Cons) sum;
            Map row = (Map) ((Cons) sc.cdr).car;

            //ISExp l = labelValue(label);
            if(row.value.containsKey(label))
            {
                return visitApply(f1, ImmutableList.of(row.value.get(label)), env, state);
            }
            return visitApply(f2, ImmutableList.of(sum), env, state);
        }

        /*@Override
        public ISExp visitMFold(ISExp func, ISExp res, ISExp map, IList env, Void state)
        {
            if(map == MNil.INSTANCE)
            {
                return res;
            }
            Map m = (Map) map;
            for (java.util.Map.Entry<? extends ISExp, ? extends ISExp> entry : m.value.entrySet())
            {
                res = visitApply(func, ImmutableList.of(res, entry.getKey(), entry.getValue()), env, state);
            }
            return res;
        }*/

        @Override
        public ISExp visitApply(ISExp func, ImmutableList<ISExp> args, IList env, Void state)
        {
            return apply(func, lift(args), state);
        }

        @Override
        public ISExp visitMap(Map map, IList env, Void state)
        {
            ImmutableMap.Builder<ISExp, ISExp> builder = ImmutableMap.builder();
            for(java.util.Map.Entry<? extends ISExp, ? extends ISExp> entry : map.value.entrySet())
            {
                builder.put(eval(entry.getKey(), env, state), eval(entry.getValue(), env, state));
            }
            return new Map(builder.build());
        }

        private ISExp unlabel(ISExp value, IList env)
        {
            if(value instanceof Cons && ((Cons) value).car.equals(labelSymbol))
            {
                if(length(value) == 3)
                {
                    Cons c2 = (Cons) ((Cons) value).cdr;
                    Cons c3 = (Cons) c2.cdr;
                    ISExp args = c2.car;
                    ISExp body = c3.car;
                    // function label
                    if(args instanceof IList)
                    {
                        return makeFunction((IList) args, body, env);
                    }
                }
                else if (length(value) == 2)
                {
                    Cons c2 = (Cons) ((Cons) value).cdr;
                    // reference label
                    return eval(c2.car, env, null);
                }
            }
            return value;
        }

        protected ISExp apply(ISExp func, IList args, Void state)
        {
            if (func instanceof ICallableExp)
            {
                return ((ICallableExp) func).apply(args);
            }
            if (func instanceof Cons)
            {
                Cons c1 = (Cons) func;
                if ((functionSymbol.equals(c1.car) || macroSymbol.equals(c1.car)) && c1.cdr != Nil.INSTANCE)
                {
                    Cons c2 = (Cons) c1.cdr;
                    if(macroSymbol.equals(c1.car) && c2.cdr != Nil.INSTANCE)
                    {
                        // skip type signature
                        c2 = (Cons) c2.cdr;
                    }
                    if (c2.car instanceof IList && c2.cdr != Nil.INSTANCE)
                    {
                        IList argNames = (IList) c2.car;
                        Cons c3 = (Cons) c2.cdr;
                        ISExp body = c3.car;
                        if (c3.cdr != Nil.INSTANCE)
                        {
                            Cons c4 = (Cons) c3.cdr;
                            ISExp env = c4.car;
                            if (env instanceof IList && c4.cdr == Nil.INSTANCE)
                            {
                                return eval(body, bind(argNames, args, (IList) env), state);
                            }
                        }
                    }
                }
            }
            if (func instanceof Map)
            {
                Map map = (Map) func;
                if(length(args) != 1)
                {
                    throw new IllegalArgumentException("Can only apply map to 1 argument");
                }
                Cons c1 = (Cons) args;
                if(map.value.containsKey(c1.car))
                {
                    return map.value.get(c1.car);
                }
                throw new IllegalStateException("Map " + map + " doesn't have key " + c1.car);
            }
            throw new IllegalArgumentException("Don't know how to apply: " + func);
        }

        boolean isMacro(Symbol name)
        {
            ISExp func = lookup(topEnv, name);
            func = unlabel(func, topEnv);
            if (func instanceof Cons)
            {
                Cons c1 = (Cons) func;
                if (macroSymbol.equals(c1.car) && c1.cdr != Nil.INSTANCE)
                {
                    // not checking further, assuming macroSymbol + length are enough
                    return true;
                }
            }
            return func instanceof IMacroExp;
        }

        ISExp expandMacro(Symbol name, IList args)
        {
            ISExp func = lookup(topEnv, name);
            func = unlabel(func, topEnv);
            if(func instanceof IMacroExp)
            {
                return ((IMacroExp) func).apply(args);
            }
            return apply(func, args, null);
        }
    }

    public static class TypeChecker extends Interpreter<ImmutableSet<ISExp>, ISExp>
    {
        private Unifier unifier;

        public TypeChecker(Function<String, ISExp> reader)
        {
            super(reader);
        }

        public TypeChecker(Function<String, ISExp> reader, ImmutableMap<? extends ISExp, ? extends ISExp> topEnv)
        {
            super(reader, topEnv);
        }

        public ISExp infer(ISExp exp, Unifier unifier)
        {
            this.unifier = unifier;
            return eval(exp, envToContext(topEnv, ImmutableSet.<ISExp>of()), ImmutableSet.<ISExp>of());
        }

        @Override
        public ISExp visitSymbol(Symbol symbol, IList env, ImmutableSet<ISExp> boundVarTypes)
        {
            ISExp type = lookup(env, symbol);
            if(type == Unbound.INSTANCE)
            {
                throw new IllegalStateException("Undefined variable type " + symbol);
            }
            Set<ISExp> set = Sets.newHashSet();
            IList list = env;
            while(list != Nil.INSTANCE)
            {
                Cons cons = (Cons) list;
                Map map = (Map) cons.car;
                set.addAll(map.value.values());
                list = cons.cdr;
            }
            if(!boundVarTypes.contains(type))
            {
                // remove only from bind env
                set.remove(type);
            }
            ISExp newType = unifier.reify(type, Maps.<ISExp, ISExp>newHashMap(), ImmutableSet.copyOf(set));
            //System.out.println("lookup-reify: " + symbol + " | " + unifier.typeToString(type) + " | " + unifier.typeToString(newType));
            return newType;
        }

        @Override
        public ISExp visitArithmSymbol(ArithmSymbol symbol, IList env, ImmutableSet<ISExp> boundVarTypes)
        {
            return ArithmOp.makeArithmType(symbol);
        }

        @Override
        public ISExp visitAtom(IAtom atom, IList env, ImmutableSet<ISExp> boundVarTypes)
        {
            return atom.getType(unifier);
        }

        @Override
        public ISExp visitQuote(ISExp arg, IList env, ImmutableSet<ISExp> boundVarTypes)
        {
            return arg.getType(unifier);
        }

        @Override
        public ISExp visitLambda(IList args, ISExp body, IList env, ImmutableSet<ISExp> boundVarTypes)
        {
            return makeFunctionType(args, body, env, boundVarTypes);
        }

        @Override
        public ISExp visitMacro(ISExp type, IList args, ISExp body, IList env, ImmutableSet<ISExp> boundVarTypes)
        {
            return unifier.reify(type, Maps.<ISExp, ISExp>newHashMap(), boundVarTypes);
        }

        @Override
        protected ISExp visitLabel(ISExp label)
        {
            return labelType(label);
        }

        @Override
        public ISExp visitFold(ISExp func, ISExp res, ISExp list, IList env, ImmutableSet<ISExp> boundVarTypes)
        {
            // ((A, B) -> A, A, list[A]) -> A
            ISExp A = unifier.getFreshVar(), B = unifier.getFreshVar();
            ISExp fType = absType(lift(A, B), A);
            unifier.unify(func, fType);
            unifier.unify(res, A);
            unifier.unify(list, PrimTypes.List.type);
            return A;
        }

        @Override
        protected ISExp visitHas(ISExp label, ISExp sum, ISExp f1, ISExp f2, IList env, ImmutableSet<ISExp> isExps)
        {
            ISExp r = unifier.getFreshVar();
            ISExp A = unifier.getFreshVar();
            ISExp B = unifier.getFreshVar();
            unifier.addLacks(r, label);
            ISExp m = makeRow(label, A, r);
            unifier.unify(sumType(m), sum);
            unifier.unify(absType(lift(A), B), f1);
            unifier.unify(absType(lift(sumType(r)), B), f2);
            return B;
        }

        /*@Override
        public ISExp visitMFold(ISExp func, ISExp res, ISExp map, IList env, ImmutableSet<ISExp> boundVarTypes)
        {
            // ((A, B, C) -> A, A, map[B, C]) -> A
            ISExp A = unifier.getFreshVar(), B = unifier.getFreshVar(), C = unifier.getFreshVar();
            ISExp fType = absType(ImmutableList.of(A, B, C), A);
            unifier.unify(func, fType);
            unifier.unify(res, A);
            unifier.unify(map, PrimTypes.Map.type);
            return A;
        }*/

        @Override
        public ISExp visitApply(ISExp func, ImmutableList<ISExp> args, IList env, ImmutableSet<ISExp> boundVarTypes)
        {
            ISExp bodyType = unifier.getFreshVar();
            unifier.unify(func, absType(lift(args), bodyType));
            return bodyType;
        }

        @Override
        public ISExp visitMap(Map map, IList env, ImmutableSet<ISExp> boundVarTypes)
        {
            ISExp cns = emptyRow();
            for(java.util.Map.Entry<? extends ISExp, ? extends ISExp> entry : map.value.entrySet())
            {
                cns = makeRow(eval(entry.getKey(), env, boundVarTypes), eval(entry.getValue(), env, boundVarTypes), cns);
            }
            ISExp ret = unifier.getFreshVar();
            unifier.unify(prodType(ret), prodType(cns));
            return prodType(ret);
        }

        @Override
        protected IList frameToContext(Map frame, IList context, ImmutableSet<ISExp> boundVarTypes)
        {
            java.util.Map<ISExp, ISExp> ctxBuilder = Maps.newHashMap();
            ImmutableSet.Builder<ISExp> builder = ImmutableSet.builder();
            builder.addAll(boundVarTypes);
            for (java.util.Map.Entry<? extends ISExp, ? extends ISExp> entry : frame.value.entrySet())
            {
                // new ones shadow old ones. TODO: unify with the list approach?
                ISExp type = unifier.getFreshVar();
                ctxBuilder.put((Symbol) entry.getKey(), type);
                builder.add(type);
            }
            IList newContext = new Cons(new Map(ImmutableMap.copyOf(ctxBuilder)), context);
            ImmutableSet<ISExp> newBoundVarTypes = builder.build();
            /*for (java.util.Map.Entry<? extends ISExp, ? extends ISExp> entry : frame.value.entrySet())
            {
                ISExp symbol = ctxBuilder.get(entry.getKey());
                Set<ISExp> bound = Sets.newHashSet(newBoundVarTypes);
                bound.remove(symbol);
                unifier.setBound((Symbol) symbol, ImmutableSet.copyOf(bound));
                // lalala everything is broken
                ImmutableSet<Symbol> substFree = unifier.substFree();
            }*/
            for (java.util.Map.Entry<? extends ISExp, ? extends ISExp> entry : frame.value.entrySet())
            {
                // Hmm
                unifier.unify(ctxBuilder.get(entry.getKey()), unlabelType(entry.getValue(), newContext, boundVarTypes));
                //inferBindPair(ctxBuilder, context, vars, entry.getKey(), entry.getValue());
            }
            return newContext;
        }

        private IList envToContext(IList env, ImmutableSet<ISExp> boundVarTypes)
        {
            if (env == Nil.INSTANCE)
            {
                return Nil.INSTANCE;
            }
            Cons cons = (Cons) env;
            return frameToContext((Map) cons.car, envToContext(cons.cdr, boundVarTypes), boundVarTypes);
        }

        private ISExp unlabelType(ISExp value, IList context, ImmutableSet<ISExp> boundVarTypes)
        {
            if(value instanceof Cons && ((Cons) value).car.equals(labelSymbol))
            {
                // FIXME
                if(length(value) == 3)
                {
                    Cons c2 = (Cons) ((Cons) value).cdr;
                    Cons c3 = (Cons) c2.cdr;
                    ISExp args = c2.car;
                    ISExp body = c3.car;
                    // function label
                    if(args instanceof IList)
                    {
                        return makeFunctionType((IList) args, body, context, boundVarTypes);
                    }
                }
                else if (length(value) == 2)
                {
                    Cons c2 = (Cons) ((Cons) value).cdr;
                    // reference label
                    return eval(c2.car, context, boundVarTypes);
                }
            }
            return value.getType(unifier);
        }

        private ISExp makeFunctionType(IList args, ISExp body, IList env, ImmutableSet<ISExp> boundVarTypes)
        {
            if(args == Nil.INSTANCE)
            {
                return absType(Nil.INSTANCE, eval(body, env, boundVarTypes));
            }
            java.util.Map<ISExp, ISExp> frame = Maps.newHashMap();
            ImmutableList.Builder<ISExp> argTypesBuilder = ImmutableList.builder();
            ImmutableSet.Builder<ISExp> builder = ImmutableSet.builder();
            builder.addAll(boundVarTypes);
            while(args != Nil.INSTANCE)
            {
                Cons c4 = (Cons) args;
                Symbol arg = (Symbol) c4.car;
                ISExp argType = unifier.getFreshVar();
                frame.put(arg, argType);
                argTypesBuilder.add(argType);
                builder.add(argType);
                args = c4.cdr;
            }
            ISExp bodyType = eval(body, new Cons(new Map(ImmutableMap.copyOf(frame)), env), builder.build());
            return absType(lift(argTypesBuilder.build()), bodyType);
        }
    }
}
